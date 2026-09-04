/*
 *  Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 *
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.cloudbees.plugins.credentials.*;
import hudson.EnvVars;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AWSClientFactoryTest {

    private static final String REGION = "us-east-1";
    private static final String codeBuildDescriptor = "descriptor";
    private static final String proxyHost = "host";
    private static final String proxyPort = "2";

    private final CodeBuildCredentials mockCBCreds = mock(CodeBuildCredentials.class);
    private final AwsSessionCredentials mockAWSCreds = mock(AwsSessionCredentials.class);
    private final DefaultCredentialsProvider cpChain = mock(DefaultCredentialsProvider.class);
    private final SystemCredentialsProvider mockSysCreds = mock(SystemCredentialsProvider.class);
    private final Secret awsSecretKey = mock(Secret.class);
    private final Run<?, ?> build = mock(Run.class);
    private final StepContext mockStepContext = mock(StepContext.class);

    private MockedStatic<CredentialsMatchers> credentialsMatchersMock;
    private MockedStatic<SystemCredentialsProvider> systemCredentialsProviderMock;
    private MockedStatic<DefaultCredentialsProvider> defaultChainMock;

    @Before
    public void setUp() {
        credentialsMatchersMock = mockStatic(CredentialsMatchers.class);
        systemCredentialsProviderMock = mockStatic(SystemCredentialsProvider.class);
        defaultChainMock = mockStatic(DefaultCredentialsProvider.class);
        when(CredentialsMatchers.firstOrNull(any(), any())).thenReturn(mockCBCreds);
        when(mockCBCreds.resolveCredentials()).thenReturn(mockAWSCreds);
        when(mockCBCreds.getCredentialsDescriptor()).thenReturn(codeBuildDescriptor);
        when(mockCBCreds.getProxyHost()).thenReturn(proxyHost);
        when(mockCBCreds.getProxyPort()).thenReturn(proxyPort);

        when(mockAWSCreds.accessKeyId()).thenReturn("a");
        when(mockAWSCreds.secretAccessKey()).thenReturn("s");
        when(mockAWSCreds.sessionToken()).thenReturn("t");
        when(SystemCredentialsProvider.getInstance()).thenReturn(mockSysCreds);

        when(DefaultCredentialsProvider.create()).thenReturn(cpChain);
        when(awsSecretKey.getPlainText()).thenReturn("s");
    }

    @After
    public void tearDown() {
        if (defaultChainMock != null) {
            defaultChainMock.close();
        }
        if (systemCredentialsProviderMock != null) {
            systemCredentialsProviderMock.close();
        }
        if (credentialsMatchersMock != null) {
            credentialsMatchersMock.close();
        }
    }

    @Test
    public void testNullInput() {
        try {
            new AWSClientFactory(null, null, null, null, null, null, null, null, build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(CodeBuilderValidation.invalidRegionError));
        }
    }

    @Test
    public void testBlankInput() {
        try {
            new AWSClientFactory("", "", "", "", "", null, "", "", build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(CodeBuilderValidation.invalidRegionError));
        }
    }

    @Test(expected=NumberFormatException.class)
    public void testInvalidProxyPort() {
        new AWSClientFactory("keys", "", "", "port", "", awsSecretKey, "", REGION, build, null);
    }

    @Test
    public void testInvalidCredsOption() {
        try {
            new AWSClientFactory("bad", "", "", "", "", null, "", REGION, build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(invalidCredTypeError));
        }
    }

    @Test
    public void testSpecifyCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "a", awsSecretKey, "t", REGION, build, null);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(parseInt(proxyPort)));

    }

    @Test
    public void testDefaultCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "", awsSecretKey, "", REGION, build, null);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(parseInt(proxyPort)));
    }



    @Test
    public void testNullCredsId() {
        try {
            new AWSClientFactory("jenkins", null, "", "", "", null, "", REGION, build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(CodeBuilderValidation.invalidCredentialsIdError));
        }
    }

    @Test
    public void testEmptyCredsId() {
        try {
            new AWSClientFactory("jenkins", "", "", "", "", null, "", REGION, build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(CodeBuilderValidation.invalidCredentialsIdError));
        }
    }

    @Test
    public void testJenkinsCreds() {
        String credentialsId = "id";
        AWSClientFactory awsClientFactory = new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build, null);

        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(parseInt(proxyPort)));
        assert(awsClientFactory.getCredentialsDescriptor().contains(codeBuildDescriptor));
        assert(awsClientFactory.getCredentialsDescriptor().contains(credentialsId));
    }

    @Test
    public void testNullAwsSecretKey() {
        try {
            new AWSClientFactory("keys", null, "", "", "a", null, "", REGION, build, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(invalidSecretKeyError));
        }
    }

    @Test
    public void testJenkinsFolderCreds() {
        String credentialsId = "folder-creds";
        String folder = "folder";

        Jenkins mockInstance = mock(Jenkins.class);
        Item mockFolder = mock(Item.class);
        try (MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class);
             MockedStatic<CredentialsProvider> credentialsProviderMock = mockStatic(CredentialsProvider.class)) {
            when(Jenkins.getInstance()).thenReturn(mockInstance);
            when(mockInstance.getItemByFullName(folder)).thenReturn(mockFolder);

            List<Credentials> mockFolderCredsList = mock(List.class);
            when(CredentialsProvider.lookupCredentials(Credentials.class, mockFolder)).thenReturn(mockFolderCredsList);

            List<Credentials> mockCredsList = mock(List.class);
            when(mockSysCreds.getCredentials()).thenReturn(mockCredsList);

            when(CredentialsMatchers.firstOrNull(eq(mockCredsList), any())).thenReturn(null);
            when(CredentialsMatchers.firstOrNull(eq(mockFolderCredsList), any())).thenReturn(mockCBCreds);

            AbstractProject mockProject = mock(AbstractProject.class);
            Jenkins mockFolderItem = mock(Jenkins.class);

            when(build.getParent()).thenReturn(mockProject);
            when(mockProject.getParent()).thenReturn(mockFolderItem);
            when(mockFolderItem.getFullName()).thenReturn(folder);

            AWSClientFactory awsClientFactory = new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build, null);

            assert(awsClientFactory.getProxyHost().equals(proxyHost));
            assert(awsClientFactory.getProxyPort().equals(parseInt(proxyPort)));
            assert(awsClientFactory.getCredentialsDescriptor().contains(codeBuildDescriptor));
            assert(awsClientFactory.getCredentialsDescriptor().contains(credentialsId));
        }
    }

    @Test(expected=InvalidInputException.class)
    public void testNonExistentCreds() {
        String credentialsId = "folder-creds";
        String folder = "folder";

        when(CredentialsMatchers.firstOrNull(any(), any())).thenReturn(null);

        Jenkins mockInstance = mock(Jenkins.class);
        Item mockFolder = mock(Item.class);
        try (MockedStatic<Jenkins> jenkinsMock = mockStatic(Jenkins.class);
             MockedStatic<CredentialsProvider> credentialsProviderMock = mockStatic(CredentialsProvider.class)) {
            when(Jenkins.getInstance()).thenReturn(mockInstance);
            when(mockInstance.getItemByFullName(credentialsId)).thenReturn(mockFolder);

            AbstractProject mockProject = mock(AbstractProject.class);
            Jenkins mockFolderItem = mock(Jenkins.class);

            when(build.getParent()).thenReturn(mockProject);
            when(mockProject.getParent()).thenReturn(mockFolderItem);
            when(mockFolder.getFullName()).thenReturn(folder);

            try {
                new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build, null);
            } catch (InvalidInputException e) {
                assert(e.getMessage().contains(CodeBuilderValidation.invalidCredentialsIdError));
                throw e;
            }
        }
    }

    @Test
    public void testStepContextBasicCreds() throws IOException, InterruptedException {
        EnvVars mockEnvVars = mock(EnvVars.class);
        when(mockEnvVars.get(AWSClientFactory.AWS_ACCESS_KEY_ID)).thenReturn("access");
        when(mockEnvVars.get(AWSClientFactory.AWS_SECRET_ACCESS_KEY)).thenReturn("secret");
        when(mockEnvVars.get(AWSClientFactory.AWS_SESSION_TOKEN)).thenReturn(null);
        when(mockStepContext.get(EnvVars.class)).thenReturn(mockEnvVars);
        when(awsSecretKey.getPlainText()).thenReturn("");

        when(DefaultCredentialsProvider.create()).thenThrow(new RuntimeException("Should not be accessing the default credentials provider chain."));

        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", "", "", "", awsSecretKey, "", REGION, build, mockStepContext);

        assert(awsClientFactory.getCredentialsDescriptor().contains(stepCredentials));
    }

    @Test
    public void testStepContextSessionCreds() throws IOException, InterruptedException {
        EnvVars mockEnvVars = mock(EnvVars.class);
        when(mockEnvVars.get(AWSClientFactory.AWS_ACCESS_KEY_ID)).thenReturn("access");
        when(mockEnvVars.get(AWSClientFactory.AWS_SECRET_ACCESS_KEY)).thenReturn("secret");
        when(mockEnvVars.get(AWSClientFactory.AWS_SESSION_TOKEN)).thenReturn("token");
        when(mockStepContext.get(EnvVars.class)).thenReturn(mockEnvVars);
        when(awsSecretKey.getPlainText()).thenReturn("");

        when(DefaultCredentialsProvider.create()).thenThrow(new RuntimeException("Should not be accessing the default credentials provider chain."));

        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", "", "", "", awsSecretKey, "", REGION, build, mockStepContext);

        assert(awsClientFactory.getCredentialsDescriptor().contains(stepCredentials));
    }

    @Test
    public void testStepContextWithKeysSpecified() throws IOException, InterruptedException {
        EnvVars mockEnvVars = mock(EnvVars.class);
        when(mockEnvVars.get(AWSClientFactory.AWS_ACCESS_KEY_ID)).thenReturn("access");
        when(mockEnvVars.get(AWSClientFactory.AWS_SECRET_ACCESS_KEY)).thenReturn("secret");
        when(mockEnvVars.get(AWSClientFactory.AWS_SESSION_TOKEN)).thenReturn("token");
        when(mockStepContext.get(EnvVars.class)).thenReturn(mockEnvVars);

        when(DefaultCredentialsProvider.create()).thenThrow(new RuntimeException("Should not be accessing the default credentials provider chain."));

        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", "", "", "accessKey", awsSecretKey, "", REGION, build, mockStepContext);

        assert(awsClientFactory.getCredentialsDescriptor().contains(CodeBuildBaseCredentials.BASIC_AWS_CREDS));
    }

    @Test
    public void testGetProjectVersionNullStreamDoesNotThrow() {
        assertEquals("", AWSClientFactory.getProjectVersion((InputStream) null));
    }

    @Test
    public void testGetProjectVersionReadsVersion() throws IOException {
        InputStream stream = new ByteArrayInputStream("version=9.9.9\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("/9.9.9", AWSClientFactory.getProjectVersion(stream));
    }

}
