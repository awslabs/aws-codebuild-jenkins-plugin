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
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({CredentialsMatchers.class, SystemCredentialsProvider.class, DefaultAWSCredentialsProviderChain.class})
public class AWSClientFactoryTest {

    private static final String REGION = "us-east-1";
    private static final String codeBuildDescriptor = "descriptor";
    private static final String proxyHost = "host";
    private static final String proxyPort = "2";

    private final CodeBuildCredentials mockCBCreds = mock(CodeBuildCredentials.class);
    private final AWSCredentials mockAWSCreds = mock(AWSCredentials.class);
    private final DefaultAWSCredentialsProviderChain cpChain = mock(DefaultAWSCredentialsProviderChain.class);
    private final SystemCredentialsProvider mockSysCreds = mock(SystemCredentialsProvider.class);

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
        when(SystemCredentialsProvider.getInstance()).thenReturn(mockSysCreds);

        when(DefaultAWSCredentialsProviderChain.getInstance()).thenReturn(cpChain);
    }

    @Test
    public void testNullInput() {
        try {
            new AWSClientFactory(null, null, null, null, null, null, null);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidRegionError));
        }
    }

    @Test
    public void testBlankInput() {
        try {
            new AWSClientFactory("", "", "", "", "", "", "");
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidRegionError));
        }
    }

    @Test(expected=NumberFormatException.class)
    public void testInvalidProxyPort() {
        new AWSClientFactory("keys", "", "", "port", "", "", REGION);
    }

    @Test
    public void testInvalidCredsOption() {
        try {
            new AWSClientFactory("bad", "", "", "", "", "", REGION);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredTypeError));
        }
    }

    @Test
    public void testInvalidCredsId() {
        try {
            new AWSClientFactory("jenkins", "", "", "", "", "", REGION);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains(Validation.invalidCredentialsIdError));
        }
    }

    @Test
    public void testJenkinsCreds() {
        String credentialsId = "id";
        AWSClientFactory awsClientFactory = new AWSClientFactory("jenkins", credentialsId, "", "", "", "", REGION);

        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));
        assert(awsClientFactory.getCredentialsDescriptor().contains(codeBuildDescriptor));
        assert(awsClientFactory.getCredentialsDescriptor().contains(credentialsId));
    }

    @Test
    public void testSpecifyCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "a", "s", REGION);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));

    }

    @Test
    public void testDefaultCreds() {
        AWSClientFactory awsClientFactory = new AWSClientFactory("keys", "", proxyHost, proxyPort, "", "", REGION);
        assert(awsClientFactory.getProxyHost().equals(proxyHost));
        assert(awsClientFactory.getProxyPort().equals(Validation.parseInt(proxyPort)));
    }

}
