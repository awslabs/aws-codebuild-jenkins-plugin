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
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.*;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import com.cloudbees.plugins.credentials.Credentials.*;
import com.cloudbees.hudson.plugins.folder.*;


import enums.CredentialsType;
import hudson.model.*;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;

public class AWSClientFactory {


    @Getter private final String proxyHost;
    @Getter private final Integer proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final String awsSecretKey;
    @Getter private final String region;

    private String credentialsDescriptor;
    private AWSCredentialsProvider awsCredentialsProvider;

    public AWSClientFactory(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey,
                       String region, Run<?, ?> build) {

        this.awsAccessKey = Validation.sanitize(awsAccessKey);
        this.awsSecretKey = Validation.sanitize(awsSecretKey);
        this.region = Validation.sanitize(region);

        Validation.checkAWSClientFactoryRegionConfig(this.region);
        this.credentialsDescriptor = "";

        if(credentialsType.equals(CredentialsType.Jenkins.toString())) {
            credentialsId = Validation.sanitize(credentialsId);
            Validation.checkAWSClientFactoryJenkinsCredentialsConfig(credentialsId);
            CodeBuildCredentials codeBuildCredentials;

            codeBuildCredentials = (CodeBuildCredentials) CredentialsMatchers.firstOrNull(SystemCredentialsProvider.getInstance().getCredentials(),
                    CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));

            if(codeBuildCredentials == null) {
                Item folder;
                Jenkins instance = Jenkins.getInstance();
                if(instance != null) {
                    folder = instance.getItemByFullName(build.getParent().getParent().getFullName());
                    codeBuildCredentials = (CodeBuildCredentials) CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(Credentials.class, folder),
                            CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId)));
                }
            }

            if(codeBuildCredentials != null) {
                this.awsCredentialsProvider = codeBuildCredentials;
                this.proxyHost = codeBuildCredentials.getProxyHost();
                this.proxyPort = Validation.parseInt(codeBuildCredentials.getProxyPort());
                this.credentialsDescriptor = codeBuildCredentials.getCredentialsDescriptor() + " (provided from Jenkins credentials " + credentialsId + ")";
            } else {
                throw new InvalidInputException(Validation.invalidCredentialsIdError);
            }
        } else if(credentialsType.equals(CredentialsType.Keys.toString())) {
            awsCredentialsProvider = getBasicCredentialsOrDefaultChain(Validation.sanitize(awsAccessKey), Validation.sanitize(awsSecretKey));
            this.proxyHost = Validation.sanitize(proxyHost);
            this.proxyPort = Validation.parseInt(proxyPort);
        } else {
            throw new InvalidInputException(Validation.invalidCredTypeError);
        }
    }

    public AWSCodeBuildClient getCodeBuildClient() throws InvalidInputException, IllegalArgumentException {
        AWSCodeBuildClient client = new AWSCodeBuildClient(awsCredentialsProvider, getClientConfiguration());
        client.setEndpoint("https://codebuild." + region + ".amazonaws.com");
        return client;
    }

    public AmazonS3Client getS3Client() throws InvalidInputException {
        return new AmazonS3Client(awsCredentialsProvider, getClientConfiguration());
    }

    public AWSLogsClient getCloudWatchLogsClient() throws InvalidInputException {
        AWSLogsClient client = new AWSLogsClient(awsCredentialsProvider, getClientConfiguration());
        client.setEndpoint("https://logs." + region + ".amazonaws.com");
        return client;
    }

    public static AWSCredentialsProvider getBasicCredentialsOrDefaultChain(String accessKey, String secretKey) {
        AWSCredentialsProvider result;
        if(StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
            result = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        } else {
            result = DefaultAWSCredentialsProviderChain.getInstance();
            try {
                result.getCredentials();
            } catch (SdkClientException e) {
                throw new InvalidInputException(Validation.invalidDefaultCredentialsError);
            }
        }
        return result;
    }

    private ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfig = new ClientConfiguration()
                .withUserAgentPrefix("CodeBuild-Jenkins-Plugin") //tags all calls made from Jenkins plugin.
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
            if(awsAccessKey.isEmpty() || awsSecretKey.isEmpty()) {
                return Validation.defaultChainCredentials;
            } else {
                return Validation.basicAWSCredentials;
            }
        } else {
            return credentialsDescriptor;
        }
    }

}
