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

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DefaultAWSCredentialsProviderChain.class)
public class AWSClientFactoryTest {

    private static final String REGION = "us-east-1";
    private final DefaultAWSCredentialsProviderChain cpChain = mock(DefaultAWSCredentialsProviderChain.class);

    @Test
    public void testValidConfigDefaultCredentialsUsed() {
        // Given
        setUpInstanceWithDefaultCredentials();

        AWSClientFactory cf = new AWSClientFactory("", "", "", "", REGION);

        // Then
        assertTrue(cf.isDefaultCredentialUsed());
    }

    @Test
    public void testValidConfigIAMCredentialsUsedOverDefaultCredentials() {
        // Given
        setUpInstanceWithDefaultCredentials();

        AWSClientFactory cf = new AWSClientFactory("", "", "iamId", "iamKey", REGION);

        // Then
        assertFalse(cf.isDefaultCredentialUsed());
    }

    @Test
    public void testValidConfigNoDefaultCredentialsIAMUsed() {
        // Given
        setUpInstanceWithNoDefaultCredentials();

        AWSClientFactory cf = new AWSClientFactory("", "", "iamId", "iamKey", REGION);

        // Then
        assertFalse(cf.isDefaultCredentialUsed());
    }

    @Test(expected=InvalidInputException.class)
    public void testInvalidConfigNoDefaultCredentialsNullKeys() {
        setUpInstanceWithNoDefaultCredentials();

        new AWSClientFactory(null, null, null, null, null);
    }

    @Test(expected=InvalidInputException.class)
    public void testInvalidConfigNoDefaultCredentialsEmptyKeys() {
        setUpInstanceWithNoDefaultCredentials();

        new AWSClientFactory("", "", "", "", REGION);
    }

    @Test(expected=InvalidInputException.class)
    public void testInvalidProxyPort() {
        new AWSClientFactory("host", "-2", "", "", REGION);
    }

    protected void setUpInstanceWithNoDefaultCredentials() {
        when(cpChain.getCredentials()).thenThrow(new SdkClientException("No Instance Profile Credentials"));
        PowerMockito.mockStatic(DefaultAWSCredentialsProviderChain.class);
        when(DefaultAWSCredentialsProviderChain.getInstance()).thenReturn(cpChain);
    }

    protected void setUpInstanceWithDefaultCredentials() {
        when(cpChain.getCredentials()).thenReturn(new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() { return null;}

            @Override
            public String getAWSSecretKey() {
                return null;
            }
        });
        PowerMockito.mockStatic(DefaultAWSCredentialsProviderChain.class);
        when(DefaultAWSCredentialsProviderChain.getInstance()).thenReturn(cpChain);
    }
}
