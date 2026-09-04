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
 *
 *
 *     Portions copyright Copyright (c) 2015, CloudBees, Inc.
 *     This program is made available under the terms of the MIT License.
 *
 *     Permission is hereby granted, free of charge, to any person obtaining a copy
 *     of this software and associated documentation files (the "Software"), to deal
 *     in the Software without restriction, including without limitation the rights
 *     to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *     copies of the Software, and to permit persons to whom the Software is
 *     furnished to do so, subject to the following conditions:
 *
 *     The above copyright notice and this permission notice shall be included in all
 *     copies or substantial portions of the Software.
 *
 *     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *     AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *     LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *     SOFTWARE.
 */

import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import enums.CredentialsType;
import hudson.EnvVars;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;


public class AWSClientFactory {

    @Getter private final String proxyHost;
    @Getter private final Integer proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final Secret awsSecretKey;
    @Getter private final String awsSessionToken;
    @Getter private final String region;

    private static final int CLIENT_CONFIG_CONNECTION_TIMEOUT = 60000;
    private static final int CLIENT_CONFIG_SOCKET_TIMEOUT = 60000;
    private static final int CLIENT_CONFIG_MAX_ERROR_RETRIES = 10;
    private static final int CLIENT_CONFIG_MAX_CONNECTIONS = 100;
    private static final int RETRY_BACKOFF_BASE_DELAY = 10000;
    private static final int RETRY_BACKOFF_MAX_DELAY = 30000;

    public static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    public static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";

    private String credentialsDescriptor;
    private AwsCredentialsProvider awsCredentialsProvider;
    private static final String POM_PROPERTIES = "/META-INF/maven/com.amazonaws/aws-codebuild/pom.properties";

    public AWSClientFactory(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, Secret awsSecretKey, String awsSessionToken,
                       String region, Run<?, ?> build, StepContext stepContext) {

        this.awsAccessKey = sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.awsSessionToken = sanitize(awsSessionToken);
        this.region = sanitize(region);

        CodeBuilderValidation.checkAWSClientFactoryRegionConfig(this.region);
        this.credentialsDescriptor = "";

        if(credentialsType.equals(CredentialsType.Jenkins.toString())) {
            credentialsId = sanitize(credentialsId);
            CodeBuilderValidation.checkAWSClientFactoryJenkinsCredentialsConfig(credentialsId);
            com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials codeBuildCredentials;

            codeBuildCredentials = (CodeBuildBaseCredentials) CredentialsMatchers.firstOrNull(SystemCredentialsProvider.getInstance().getCredentials(),
                    CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));

            if(codeBuildCredentials == null) {
                Item folder;
                Jenkins instance = Jenkins.getInstance();
                if(instance != null) {
                    folder = instance.getItemByFullName(build.getParent().getParent().getFullName());
                    codeBuildCredentials = (CodeBuildBaseCredentials) CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(Credentials.class, folder),
                            CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
                }
            }

            if(codeBuildCredentials != null) {
                this.awsCredentialsProvider = codeBuildCredentials;
                this.proxyHost = codeBuildCredentials.getProxyHost();
                this.proxyPort = parseInt(codeBuildCredentials.getProxyPort());
                this.credentialsDescriptor = codeBuildCredentials.getCredentialsDescriptor() + " (provided from Jenkins credentials " + credentialsId + ")";
            } else {
                throw InvalidInputException.builder().message(CodeBuilderValidation.invalidCredentialsIdError).build();
            }
        } else if(credentialsType.equals(CredentialsType.Keys.toString())) {
            if(this.awsSecretKey == null) {
                throw InvalidInputException.builder().message(invalidSecretKeyError).build();
            }

            if(stepContext != null && awsAccessKey.isEmpty() && awsSecretKey.getPlainText().isEmpty()) {
                try {
                    EnvVars stepEnvVars = stepContext.get(EnvVars.class);
                    awsCredentialsProvider = getStepCreds(stepEnvVars);
                } catch (IOException|InterruptedException e) {}
            }

            if(awsCredentialsProvider == null) {
                awsCredentialsProvider = getBasicCredentialsOrDefaultChain(sanitize(awsAccessKey), awsSecretKey.getPlainText(), sanitize(awsSessionToken));
            }
            this.proxyHost = sanitize(proxyHost);
            this.proxyPort = parseInt(proxyPort);
        } else {
            throw InvalidInputException.builder().message(invalidCredTypeError).build();
        }
    }

    public CodeBuildClient getCodeBuildClient() throws InvalidInputException, IllegalArgumentException {
        return CodeBuildClient.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(getHttpClientBuilder())
                .overrideConfiguration(getOverrideConfiguration())
                .endpointOverride(URI.create("https://codebuild." + region + getAwsClientSuffix(region)))
                .build();
    }

    public S3Client getS3Client() throws InvalidInputException {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(getHttpClientBuilder())
                .overrideConfiguration(getOverrideConfiguration())
                .endpointOverride(URI.create("https://s3." + region + getAwsClientSuffix(region)))
                .build();
    }

    public CloudWatchLogsClient getCloudWatchLogsClient() throws InvalidInputException {
        return CloudWatchLogsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(getHttpClientBuilder())
                .overrideConfiguration(getOverrideConfiguration())
                .endpointOverride(URI.create("https://logs." + region + getAwsClientSuffix(region)))
                .build();
    }

    private AwsCredentialsProvider getStepCreds(EnvVars stepEnvVars) {
        String stepAccessKey = stepEnvVars.get(AWS_ACCESS_KEY_ID);
        String stepSecretKey = stepEnvVars.get(AWS_SECRET_ACCESS_KEY);
        String stepSessionToken = stepEnvVars.get(AWS_SESSION_TOKEN);

        if(stepAccessKey != null && !stepAccessKey.isEmpty() && stepSecretKey != null && !stepSecretKey.isEmpty()) {
            this.credentialsDescriptor = stepCredentials;
            if(stepSessionToken != null && !stepSessionToken.isEmpty()) {
                return StaticCredentialsProvider.create(AwsSessionCredentials.create(stepAccessKey, stepSecretKey, stepSessionToken));
            } else {
                return StaticCredentialsProvider.create(AwsBasicCredentials.create(stepAccessKey, stepSecretKey));
            }
        }

        return null;
    }

    private ApacheHttpClient.Builder getHttpClientBuilder() {
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofMillis(CLIENT_CONFIG_CONNECTION_TIMEOUT))
                .socketTimeout(Duration.ofMillis(CLIENT_CONFIG_SOCKET_TIMEOUT))
                .maxConnections(CLIENT_CONFIG_MAX_CONNECTIONS);

        if(proxyHost != null && !proxyHost.isEmpty()) {
            StringBuilder endpoint = new StringBuilder("http://").append(proxyHost);
            if(proxyPort != null) {
                endpoint.append(":").append(proxyPort);
            }
            httpClientBuilder.proxyConfiguration(ProxyConfiguration.builder()
                    .endpoint(URI.create(endpoint.toString()))
                    .build());
        }
        return httpClientBuilder;
    }

    private ClientOverrideConfiguration getOverrideConfiguration() {
        String projectVersion = getProjectVersion();

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .numRetries(CLIENT_CONFIG_MAX_ERROR_RETRIES)
                .retryCondition(new CodeBuildClientRetryCondition())
                .backoffStrategy(EqualJitterBackoffStrategy.builder()
                        .baseDelay(Duration.ofMillis(RETRY_BACKOFF_BASE_DELAY))
                        .maxBackoffTime(Duration.ofMillis(RETRY_BACKOFF_MAX_DELAY))
                        .build())
                .build();

        return ClientOverrideConfiguration.builder()
                .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_PREFIX, "CodeBuild-Jenkins-Plugin" + projectVersion)
                .retryPolicy(retryPolicy)
                .build();
    }

    static String getProjectVersion() {
        try (InputStream stream = AWSClientFactory.class.getResourceAsStream(POM_PROPERTIES)) {
            return getProjectVersion(stream);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static String getProjectVersion(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version");
            return version == null ? "" : "/" + version;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    public String getCredentialsDescriptor() {
        if(this.credentialsDescriptor.isEmpty()) {
            if(awsAccessKey.isEmpty()) {
                return CodeBuildBaseCredentials.DEFAULT_CHAIN_CREDS;
            } else {
                return CodeBuildBaseCredentials.BASIC_AWS_CREDS;
            }
        } else {
            return credentialsDescriptor;
        }
    }

    private String getAwsClientSuffix(String region) {
        if(region.equals(Region.CN_NORTH_1.id()) || region.equals(Region.CN_NORTHWEST_1.id())) {
            return ".amazonaws.com.cn";
        } else {
            return ".amazonaws.com";
        }
    }

}
