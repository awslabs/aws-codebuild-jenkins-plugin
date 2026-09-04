/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  SPDX-License-Identifier: Apache-2.0
 */

import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.model.Item;
import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.sts.StsClient;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Regression tests for SECURITY-3773 / CVE-2026-70447: descriptor methods that
 * enumerate CodeBuildCredentials IDs must require permission, otherwise any
 * authenticated user can enumerate credentials IDs registered on the master.
 */
public class Security3773Test {

    private static final String CRED_ID = "security-3773-cred-id";
    private static final String ADMIN = "admin";
    private static final String READER = "reader";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private void setup() throws IOException {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)
                .grant(Jenkins.READ).everywhere().to(READER));

        CodeBuildBaseCredentials cred = new CodeBuildBaseCredentials(
                CredentialsScope.GLOBAL, CRED_ID, "desc",
                "accessKey", "secretKey", "", "", "", "");
        SystemCredentialsProvider.getInstance().getCredentials().add(cred);
        SystemCredentialsProvider.getInstance().save();
    }

    private boolean modelContainsCredId(ListBoxModel model) {
        for (ListBoxModel.Option o : model) {
            if (CRED_ID.equals(o.value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void codeBuilderReaderCannotEnumerateCredentials() throws IOException {
        setup();
        CodeBuilder.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuilder.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(READER, true))) {
            ListBoxModel model = d.doFillCredentialsIdItems(null, "");
            assertFalse("reader without EXTENDED_READ must not enumerate credentials",
                    modelContainsCredId(model));
        }
    }

    @Test
    public void codeBuilderAdminCanEnumerateCredentials() throws IOException {
        setup();
        CodeBuilder.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuilder.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(ADMIN, true))) {
            ListBoxModel model = d.doFillCredentialsIdItems(null, "");
            assertTrue("admin must be able to enumerate the registered credential",
                    modelContainsCredId(model));
        }
    }

    @Test
    public void codeBuildStepReaderCannotEnumerateCredentials() throws IOException {
        setup();
        CodeBuildStep.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuildStep.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(READER, true))) {
            ListBoxModel model = d.doFillCredentialsIdItems(null, "");
            assertFalse("reader without EXTENDED_READ must not enumerate credentials",
                    modelContainsCredId(model));
        }
    }

    @Test
    public void codeBuildStepAdminCanEnumerateCredentials() throws IOException {
        setup();
        CodeBuildStep.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuildStep.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(ADMIN, true))) {
            ListBoxModel model = d.doFillCredentialsIdItems(null, "");
            assertTrue("admin must be able to enumerate the registered credential",
                    modelContainsCredId(model));
        }
    }

    // ---- Fix #10: folder-scoped enumeration branch (item != null) ----

    private static final String FOLDER_EXT_READER = "folderExtReader";
    private static final String FOLDER_PLAIN_READER = "folderPlainReader";

    private Folder setupFolder() throws IOException {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        Folder folder = j.jenkins.createProject(Folder.class, "test-folder");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to(FOLDER_EXT_READER, FOLDER_PLAIN_READER)
                // EXTENDED_READ on the folder lets this user enumerate credentials in that scope
                .grant(Item.EXTENDED_READ, Item.READ).onItems(folder).to(FOLDER_EXT_READER)
                // plain reader has folder READ but NOT EXTENDED_READ / USE_ITEM
                .grant(Item.READ).onItems(folder).to(FOLDER_PLAIN_READER));

        CodeBuildBaseCredentials cred = new CodeBuildBaseCredentials(
                CredentialsScope.GLOBAL, CRED_ID, "desc",
                "accessKey", "secretKey", "", "", "", "");
        SystemCredentialsProvider.getInstance().getCredentials().add(cred);
        SystemCredentialsProvider.getInstance().save();
        return folder;
    }

    @Test
    public void folderScopedExtendedReadCanEnumerateCredentials() throws IOException {
        Folder folder = setupFolder();
        CodeBuilder.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuilder.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(FOLDER_EXT_READER, true))) {
            ListBoxModel model = d.doFillCredentialsIdItems(folder, "");
            assertTrue("EXTENDED_READ on the folder must permit enumerating credentials",
                    modelContainsCredId(model));
        }
    }

    @Test
    public void folderScopedWithoutExtendedReadGetsOnlyCurrentValue() throws IOException {
        Folder folder = setupFolder();
        CodeBuilder.DescriptorImpl d = j.jenkins.getDescriptorByType(CodeBuilder.DescriptorImpl.class);
        try (ACLContext ignored = ACL.as(User.getById(FOLDER_PLAIN_READER, true))) {
            // Passing the current value: the descriptor may only echo it back, never enumerate the rest.
            ListBoxModel model = d.doFillCredentialsIdItems(folder, CRED_ID);
            assertEquals("without EXTENDED_READ the model must contain only the echoed current value",
                    1, model.size());
            assertEquals(CRED_ID, model.get(0).value);
        }
    }

    // ---- Fix #10: doCheck guard must short-circuit without any AWS/STS interaction ----

    @Test
    public void doCheckIamRoleArnDeniedReturnsOkAndMakesNoStsCall() throws IOException {
        setup(); // ADMIN + READER realm; READER is not an administrator
        CodeBuildBaseCredentials.DescriptorImpl d =
                j.jenkins.getDescriptorByType(CodeBuildBaseCredentials.DescriptorImpl.class);
        try (MockedStatic<StsClient> stsStatic = mockStatic(StsClient.class);
             ACLContext ignored = ACL.as(User.getById(READER, true))) {
            // item == null with a non-admin user -> permission denied -> ok() before any client build
            FormValidation fv = d.doCheckIamRoleArn(null, "", "", "ak", "sk",
                    "arn:aws:iam::123456789012:role/role", "");
            assertEquals(FormValidation.Kind.OK, fv.kind);
            stsStatic.verifyNoInteractions();
        }
    }

}
