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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.amazonaws.auth.profile.internal.ProfileKeyConstants.*;
import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;


public class AWSClientFactory {

    @Getter private final String proxyHost;
    @Getter private final Integer proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final Secret awsSecretKey;
    @Getter private final String awsSessionToken;
    @Getter private final String region;

    private String credentialsDescriptor;
    private AWSCredentialsProvider awsCredentialsProvider;
    private final Properties properties;
    private static final String POM_PROPERTIES = "/META-INF/maven/com.amazonaws/aws-codebuild/pom.properties";

    public AWSClientFactory(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, Secret awsSecretKey, String awsSessionToken,
                       String region, Run<?, ?> build, StepContext stepContext) {

        this.awsAccessKey = sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.awsSessionToken = sanitize(awsSessionToken);
        this.region = sanitize(region);
        this.properties = new Properties();

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
                throw new InvalidInputException(CodeBuilderValidation.invalidCredentialsIdError);
            }
        } else if(credentialsType.equals(CredentialsType.Keys.toString())) {
            if(this.awsSecretKey == null) {
                throw new InvalidInputException(invalidSecretKeyError);
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
            throw new InvalidInputException(invalidCredTypeError);
        }
    }

    public AWSCodeBuildClient getCodeBuildClient() throws InvalidInputException, IllegalArgumentException {
        AWSCodeBuildClient client = new AWSCodeBuildClient(awsCredentialsProvider, getClientConfiguration());
        client.setEndpoint("https://codebuild." + region + getAwsClientSuffix(region));
        return client;
    }

    public AmazonS3Client getS3Client() throws InvalidInputException {
        AmazonS3Client client = new AmazonS3Client(awsCredentialsProvider, getClientConfiguration());
        client.setEndpoint("https://s3." + region + getAwsClientSuffix(region));
        return client;
    }

    public AWSLogsClient getCloudWatchLogsClient() throws InvalidInputException {
        AWSLogsClient client = new AWSLogsClient(awsCredentialsProvider, getClientConfiguration());
        client.setEndpoint("https://logs." + region + getAwsClientSuffix(region));
        return client;
    }

    private AWSCredentialsProvider getStepCreds(EnvVars stepEnvVars) {
        String stepAccessKey = stepEnvVars.get(AWS_ACCESS_KEY_ID);
        String stepSecretKey = stepEnvVars.get(AWS_SECRET_ACCESS_KEY);
        String stepSessionToken = stepEnvVars.get(AWS_SESSION_TOKEN);

        if(stepAccessKey != null && !stepAccessKey.isEmpty() && stepSecretKey != null && !stepSecretKey.isEmpty()) {
            this.credentialsDescriptor = stepCredentials;
            if(stepSessionToken != null && !stepSessionToken.isEmpty()) {
                return new AWSStaticCredentialsProvider(new BasicSessionCredentials(stepAccessKey, stepSecretKey, stepSessionToken));
            } else {
                return new AWSStaticCredentialsProvider(new BasicAWSCredentials(stepAccessKey, stepSecretKey));
            }
        }

        return null;
    }

    private ClientConfiguration getClientConfiguration() {
        String projectVersion = "";
        try(InputStream stream = this.getClass().getResourceAsStream(POM_PROPERTIES)) {
            properties.load(stream);
            projectVersion =  "/" + properties.getProperty("version");
        } catch (IOException e) {}

        ClientConfiguration clientConfig = new ClientConfiguration()
                .withUserAgentPrefix("CodeBuild-Jenkins-Plugin" + projectVersion)
                .withProxyHost(proxyHost)
                .withRetryPolicy(new RetryPolicy(new CodeBuildClientRetryCondition(),
                        new PredefinedBackoffStrategies.ExponentialBackoffStrategy(10000, 30000),
                        10, true));

        if(proxyPort != null) {
            clientConfig.setProxyPort(proxyPort);
        }
        return clientConfig;
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
        if(region.equals(Regions.CN_NORTH_1.getName().toString()) || region.equals(Regions.CN_NORTHWEST_1.getName().toString())) {
            return ".amazonaws.com.cn";
        } else {
            return ".amazonaws.com";
        }
    }

}
