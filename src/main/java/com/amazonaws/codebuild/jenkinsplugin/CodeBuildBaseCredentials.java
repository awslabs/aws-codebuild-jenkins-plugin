/*
 *     Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
package com.amazonaws.codebuild.jenkinsplugin;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.ListProjectsRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.net.URI;
import java.util.Date;
import java.util.UUID;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;

public class CodeBuildBaseCredentials extends BaseStandardCredentials implements AwsCredentialsProvider {

    public static final String DEFAULT_CHAIN_CREDS = "Using credentials provided by the DefaultAWSCredentialsProviderChain for authorization";
    public static final String BASIC_AWS_CREDS = "Using given AWS access and secret key for authorization";
    public static final String IAM_ROLE_CREDS = "Authorizing with the IAM role defined in credentials ";
    public static final String ROLE_SESSION_NAME = "CodeBuild-Jenkins-Plugin";


    public static final long serialVersionUID = 555L;
    private static final int MIN_VALIDITY_ALLOWED = 60 * 3 * 1000; //3 minutes in milliseconds

    @Getter @Setter private final String accessKey;
    @Getter @Setter private final String secretKey;
    @Getter @Setter private final String proxyHost;
    @Getter @Setter private final String proxyPort;
    @Getter @Setter private final String iamRoleArn;
    @Getter @Setter private final String externalId;

    transient private Credentials roleCredentials = null;

    @DataBoundConstructor
    public CodeBuildBaseCredentials(CredentialsScope scope, String id, String description, String accessKey, String secretKey,
                                String proxyHost, String proxyPort, String iamRoleArn, String externalId) {
        super(scope, id, description);
        this.accessKey = Validation.sanitize(accessKey);
        this.secretKey = Validation.sanitize(secretKey);
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.iamRoleArn = Validation.sanitize(iamRoleArn);
        this.externalId = externalId;
    }

    public String getCredentialsDescriptor() {
        if(accessKey.isEmpty() || secretKey.isEmpty()) {
            return DEFAULT_CHAIN_CREDS;
        } else {
           if(iamRoleArn.isEmpty()) {
               return BASIC_AWS_CREDS;
           } else {
               return IAM_ROLE_CREDS + this.iamRoleArn;
           }
        }
    }

    @Override
    public synchronized AwsCredentials resolveCredentials() {
        AwsCredentialsProvider credentialsProvider = getBasicCredentialsOrDefaultChain(accessKey, secretKey);
        AwsCredentials credentials = credentialsProvider.resolveCredentials();

        if (!iamRoleArn.isEmpty()) {
            if (haveCredentialsExpired()) {
                refresh();
            }
            Credentials snapshot = roleCredentials;
            credentials = AwsSessionCredentials.create(
                    snapshot.accessKeyId(),
                    snapshot.secretAccessKey(),
                    snapshot.sessionToken());
        }

        return credentials;
    }

    public synchronized void refresh() {
        if (!iamRoleArn.isEmpty()) {
            if (!haveCredentialsExpired()) {
                return;
            }

            AwsCredentialsProvider credentialsProvider = getBasicCredentialsOrDefaultChain(accessKey, secretKey);
            AwsCredentials credentials = credentialsProvider.resolveCredentials();

            AssumeRoleRequest assumeRequest = AssumeRoleRequest.builder()
                    .roleArn(iamRoleArn)
                    .externalId(externalId)
                    .durationSeconds(3600)
                    .roleSessionName(ROLE_SESSION_NAME)
                    .build();

            AssumeRoleResponse assumeResult;
            try (StsClient stsClient = StsClient.builder()
                    .region(Region.AWS_GLOBAL)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build()) {
                assumeResult = stsClient.assumeRole(assumeRequest);
            }

            roleCredentials = assumeResult.credentials();
        }
    }

    private boolean haveCredentialsExpired() {
        if (roleCredentials == null
                || roleCredentials.expiration().toEpochMilli() < (new Date().getTime() + MIN_VALIDITY_ALLOWED)) {
            return true;
        }

        return false;
    }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor {

        private static final int ERROR_MESSAGE_MAX_LENGTH = 178;

        static String truncateErrorMessage(String errorMessage) {
            if (errorMessage == null) {
                return "Unknown error";
            }
            return errorMessage.substring(0, Math.min(errorMessage.length(), ERROR_MESSAGE_MAX_LENGTH));
        }

        public String getDisplayName() {
            return "CodeBuild Credentials (Groovy-compatible)";
        }

        private boolean hasCredentialsValidationPermission(Item item) {
            if (item == null) {
                return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
            }
            return item.hasPermission(Item.EXTENDED_READ) || item.hasPermission(CredentialsProvider.USE_ITEM);
        }

        public FormValidation doCheckSecretKey(@AncestorInPath Item item,
                                               @QueryParameter("proxyHost") final String proxyHost,
                                               @QueryParameter("proxyPort") final String proxyPort,
                                               @QueryParameter("accessKey") final String accessKey,
                                               @QueryParameter("secretKey") final String secretKey) {

            // SECURITY-3773: ok() (not an error) for unpermitted users, so the form leaks nothing
            if (!hasCredentialsValidationPermission(item)) {
                return FormValidation.ok();
            }

            try {
                AwsCredentialsProvider initialCredentials = getBasicCredentialsOrDefaultChain(accessKey, secretKey);
                CodeBuildClient client = CodeBuildClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(initialCredentials)
                        .httpClientBuilder(getHttpClientBuilder(proxyHost, proxyPort))
                        .build();
                client.listProjects(ListProjectsRequest.builder().build());

            } catch (Exception e) {
                return FormValidation.error("Authorization failed: " + truncateErrorMessage(e.getMessage()));
            }
            return FormValidation.ok("AWS access and secret key authorization successful.");
        }

        public FormValidation doCheckIamRoleArn(@AncestorInPath Item item,
                                                @QueryParameter("proxyHost") final String proxyHost,
                                                @QueryParameter("proxyPort") final String proxyPort,
                                                @QueryParameter("accessKey") final String accessKey,
                                                @QueryParameter("secretKey") final String secretKey,
                                                @QueryParameter("iamRoleArn") final String iamRoleArn,
                                                @QueryParameter("externalId") final String externalId) {

            // SECURITY-3773: ok() (not an error) for unpermitted users, so the form leaks nothing
            if (!hasCredentialsValidationPermission(item)) {
                return FormValidation.ok();
            }

            if (accessKey.isEmpty() || secretKey.isEmpty()) {
                return FormValidation.error("AWS access and secret keys are required to use an IAM role for authorization");
            }

            if(iamRoleArn.isEmpty()) {
                return FormValidation.ok();
            }

            try {

                AwsCredentialsProvider initialCredentials = StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey));

                AssumeRoleRequest assumeRequest = AssumeRoleRequest.builder()
                        .roleArn(iamRoleArn)
                        .externalId(externalId)
                        .durationSeconds(3600)
                        .roleSessionName(ROLE_SESSION_NAME)
                        .build();

                try (StsClient stsClient = StsClient.builder()
                        .region(Region.AWS_GLOBAL)
                        .credentialsProvider(initialCredentials)
                        .httpClientBuilder(getHttpClientBuilder(proxyHost, proxyPort))
                        .build()) {
                    stsClient.assumeRole(assumeRequest);
                }

            } catch (Exception e) {
                return FormValidation.error("Authorization failed: " + truncateErrorMessage(e.getMessage()));
            }
            return FormValidation.ok("IAM role authorization successful.");
        }

        public String getNewUUID() {
            return UUID.randomUUID().toString();
        }

        private ApacheHttpClient.Builder getHttpClientBuilder(String proxyHost, String proxyPort) {
            ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
            if (proxyHost != null && !proxyHost.isEmpty()) {
                StringBuilder endpoint = new StringBuilder("http://").append(proxyHost);
                if (proxyPort != null && !proxyPort.isEmpty()) {
                    endpoint.append(":").append(Validation.parseInt(proxyPort));
                }
                httpClientBuilder.proxyConfiguration(ProxyConfiguration.builder()
                        .endpoint(URI.create(endpoint.toString()))
                        .build());
            }
            return httpClientBuilder;
        }
    }
}
