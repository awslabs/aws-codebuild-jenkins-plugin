/*
 *     Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 */

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.AmazonClientException;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

public class AWSClientFactory {

    @Getter private boolean isInstanceProfileCredentialUsed = true;
    private final String region;
    private ClientConfiguration clientConfig;
    private AWSCredentials awsCredentials;

    public AWSClientFactory(String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey, String region) throws InvalidInputException {

        // Priority is IAM credential > Instance Profile Credential
        try {
            AWSCredentialsProvider cp = InstanceProfileCredentialsProvider.getInstance();
            awsCredentials = cp.getCredentials();

            // Overwrite if IAM Credential is specified
            if(StringUtils.isNotEmpty(awsAccessKey) && StringUtils.isNotEmpty(awsSecretKey)) {
                awsCredentials = new BasicAWSCredentials(awsAccessKey,awsSecretKey);
                isInstanceProfileCredentialUsed = false;
            }
        } catch (AmazonClientException e) {
            Validation.checkAWSClientFactoryCredentialConfig(awsAccessKey, awsSecretKey);
            awsCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey);
            isInstanceProfileCredentialUsed = false;
        }

        this.region = region;

        clientConfig = new ClientConfiguration();
        clientConfig.setUserAgentPrefix("CodeBuild-Jenkins-Plugin"); //tags all calls made from Jenkins plugin.
        Validation.checkAWSClientFactoryProxyConfig(proxyHost, proxyPort);
        clientConfig.setProxyHost(proxyHost);
        if(Validation.parseInt(proxyPort) != null) {
            clientConfig.setProxyPort(Validation.parseInt(proxyPort));
        }
    }

    public AWSCodeBuildClient getCodeBuildClient() throws InvalidInputException {
        AWSCodeBuildClient client = new AWSCodeBuildClient(awsCredentials, clientConfig);
        client.setEndpoint("https://codebuild." + region + ".amazonaws.com");
        return client;
    }

    public AmazonS3Client getS3Client() throws InvalidInputException {
        return new AmazonS3Client(awsCredentials, clientConfig);
    }

    public AWSLogsClient getCloudWatchLogsClient() throws InvalidInputException {
        AWSLogsClient client = new AWSLogsClient(awsCredentials, clientConfig);
        client.setEndpoint("https://logs." + region + ".amazonaws.com");
        return client;
    }
}
