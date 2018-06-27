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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.cloudbees.plugins.credentials.*;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({CredentialsMatchers.class, CredentialsProvider.class, SystemCredentialsProvider.class, DefaultAWSCredentialsProviderChain.class, Jenkins.class, Secret.class})
public class AWSClientFactoryTest {

    private static final String REGION = "us-east-1";
    private static final String codeBuildDescriptor = "descriptor";
    private static final String proxyHost = "host";
    private static final String proxyPort = "2";

    private final CodeBuildCredentials mockCBCreds = mock(CodeBuildCredentials.class);
    private final AWSSessionCredentials mockAWSCreds = mock(BasicSessionCredentials.class);
    private final DefaultAWSCredentialsProviderChain cpChain = mock(DefaultAWSCredentialsProviderChain.class);
    private final SystemCredentialsProvider mockSysCreds = mock(SystemCredentialsProvider.class);
    private final Secret awsSecretKey = PowerMockito.mock(Secret.class);
    private final Run<?, ?> build = mock(Run.class);

    @Before
    public void setUp() {
        PowerMockito.mockStatic(CredentialsMatchers.class);
        PowerMockito.mockStatic(SystemCredentialsProvider.class);
        PowerMockito.mockStatic(DefaultAWSCredentialsProviderChain.class);
        when(CredentialsMatchers.firstOrNull(any(Iterable.class), any(CredentialsMatcher.class))).thenReturn(mockCBCreds);
        when(mockCBCreds.getCredentials()).thenReturn(mockAWSCreds);
        when(mockCBCreds.getCredentialsDescriptor()).thenReturn(codeBuildDescriptor);
        when(mockCBCreds.getProxyHost()).thenReturn(proxyHost);
        when(mockCBCreds.getProxyPort()).thenReturn(proxyPort);

        when(mockAWSCreds.getAWSAccessKeyId()).thenReturn("a");
        when(mockAWSCreds.getAWSSecretKey()).thenReturn("s");
        when(mockAWSCreds.getSessionToken()).thenReturn("t");
        when(SystemCredentialsProvider.getInstance()).thenReturn(mockSysCreds);

        when(DefaultAWSCredentialsProviderChain.getInstance()).thenReturn(cpChain);
        when(awsSecretKey.getPlainText()).thenReturn("s");
    }

    @Test
    public void testNullInput() {
        try {
            new AWSClientFactory(null, null, null, null, null, null, null, null, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidRegionError));
        }
    }

    @Test
    public void testBlankInput() {
        try {
            new AWSClientFactory("", "", "", "", "", null, "", "", build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidRegionError));
        }
    }

    @Test(expected=NumberFormatException.class)
    public void testInvalidProxyPort() {
        new AWSClientFactory("keys", "", "", "port", "", awsSecretKey, "", REGION, build);
    }

    @Test
    public void testInvalidCredsOption() {
        try {
            new AWSClientFactory("bad", "", "", "", "", null, "", REGION, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredTypeError));
        }
    }

    @Test
    public void testSpecifyCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "a", awsSecretKey, "t", REGION, build);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));

    }

    @Test
    public void testDefaultCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "", awsSecretKey, "", REGION, build);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));
    }



    @Test
    public void testNullCredsId() {
        try {
            new AWSClientFactory("jenkins", null, "", "", "", null, "", REGION, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredentialsIdError));
        }
    }

    @Test
    public void testEmptyCredsId() {
        try {
            new AWSClientFactory("jenkins", "", "", "", "", null, "", REGION, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredentialsIdError));
        }
    }

    @Test
    public void testJenkinsCreds() {
        String credentialsId = "id";
        AWSClientFactory awsClientFactory = new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build);

        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));
        assert(awsClientFactory.getCredentialsDescriptor().contains(codeBuildDescriptor));
        assert(awsClientFactory.getCredentialsDescriptor().contains(credentialsId));
    }

    @Test
    public void testNullAwsSecretKey() {
        try {
            new AWSClientFactory("keys", null, "", "", "a", null, "", REGION, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidSecretKeyError));
        }
    }

    @Test
    public void testJenkinsFolderCreds() {
        String credentialsId = "folder-creds";
        String folder = "folder";

        Jenkins mockInstance = mock(Jenkins.class);
        Item mockFolder = mock(Item.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(mockInstance);
        when(mockInstance.getItemByFullName(folder)).thenReturn(mockFolder);

        PowerMockito.mockStatic(CredentialsProvider.class);
        List<Credentials> mockFolderCredsList = mock(List.class);
        when(CredentialsProvider.lookupCredentials(Credentials.class, mockFolder)).thenReturn(mockFolderCredsList);

        List<Credentials> mockCredsList = mock(List.class);
        when(mockSysCreds.getCredentials()).thenReturn(mockCredsList);

        when(CredentialsMatchers.firstOrNull(eq(mockCredsList), any(CredentialsMatcher.class))).thenReturn(null);
        when(CredentialsMatchers.firstOrNull(eq(mockFolderCredsList), any(CredentialsMatcher.class))).thenReturn(mockCBCreds);

        AbstractProject mockProject = mock(AbstractProject.class);
        ItemGroup mockFolderItem = mock(ItemGroup.class);

        when(build.getParent()).thenReturn(mockProject);
        when(mockProject.getParent()).thenReturn(mockFolderItem);
        when(mockFolderItem.getFullName()).thenReturn(folder);

        AWSClientFactory awsClientFactory = new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build);

        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));
        assert(awsClientFactory.getCredentialsDescriptor().contains(codeBuildDescriptor));
        assert(awsClientFactory.getCredentialsDescriptor().contains(credentialsId));
    }

    @Test(expected=InvalidInputException.class)
    public void testNonExistentCreds() {
        String credentialsId = "folder-creds";
        String folder = "folder";

        when(CredentialsMatchers.firstOrNull(any(Iterable.class), any(CredentialsMatcher.class))).thenReturn(null);

        Jenkins mockInstance = mock(Jenkins.class);
        Item mockFolder = mock(Item.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(mockInstance);
        when(mockInstance.getItemByFullName(credentialsId)).thenReturn(mockFolder);

        PowerMockito.mockStatic(CredentialsProvider.class);

        AbstractProject mockProject = mock(AbstractProject.class);
        ItemGroup mockFolderItem = mock(ItemGroup.class);

        when(build.getParent()).thenReturn(mockProject);
        when(mockProject.getParent()).thenReturn(mockFolderItem);
        when(mockFolder.getFullName()).thenReturn(folder);

        try {
            new AWSClientFactory("jenkins", credentialsId, "", "", "", null, "", REGION, build);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredentialsIdError));
            throw e;
        }
    }

}
