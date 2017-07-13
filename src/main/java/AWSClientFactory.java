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
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;


public class AWSClientFactory {

    @Getter private final boolean defaultCredentialsUsed;
    private final String region;
    private ClientConfiguration clientConfig;
    private AWSCredentialsProvider awsCredentialsProvider;

    public AWSClientFactory(String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey, String region) throws InvalidInputException {

        // Priority is IAM credential > Credentials provided by the default AWS credentials provider
        if(StringUtils.isNotEmpty(awsAccessKey) && StringUtils.isNotEmpty(awsSecretKey)) {
            defaultCredentialsUsed = false;
            awsCredentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey,awsSecretKey));
        } else {
            defaultCredentialsUsed = true;
            awsCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance();
            // validate if credentials can be loaded from any provider
            try {
                awsCredentialsProvider.getCredentials();
            } catch (SdkClientException e) {
                throw new InvalidInputException(Validation.invalidKeysError);
            }
        }

        Validation.checkAWSClientFactoryRegionConfig(region);
        this.region = region;

        Validation.checkAWSClientFactoryProxyConfig(proxyHost, proxyPort);
        clientConfig = new ClientConfiguration()
                .withUserAgentPrefix("CodeBuild-Jenkins-Plugin") //tags all calls made from Jenkins plugin.
                .withProxyHost(proxyHost)
                .withRetryPolicy(new RetryPolicy(new CodeBuildClientRetryCondition(),
                        new PredefinedBackoffStrategies.ExponentialBackoffStrategy(5000, 20000),
                        10, true));
        if(Validation.parseInt(proxyPort) != null) {
            clientConfig = clientConfig.withProxyPort(Validation.parseInt(proxyPort));
        }
    }

    public AWSCodeBuildClient getCodeBuildClient() throws InvalidInputException, IllegalArgumentException {
        AWSCodeBuildClient client = new AWSCodeBuildClient(awsCredentialsProvider, clientConfig);
        client.setEndpoint("https://codebuild." + region + ".amazonaws.com");
        return client;
    }

    public AmazonS3Client getS3Client() throws InvalidInputException {
        return new AmazonS3Client(awsCredentialsProvider, clientConfig);
    }

    public AWSLogsClient getCloudWatchLogsClient() throws InvalidInputException {
        AWSLogsClient client = new AWSLogsClient(awsCredentialsProvider, clientConfig);
        client.setEndpoint("https://logs." + region + ".amazonaws.com");
        return client;
    }
}
