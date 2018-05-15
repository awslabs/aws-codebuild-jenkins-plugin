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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.*;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import hudson.FilePath;

import java.util.Collection;

import static enums.SourceControlType.JenkinsSource;
import static enums.SourceControlType.ProjectSource;
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;

public class Validation {

    public static final String validSourceUrlPrefix = "https://";

    //AWSClientFactory
    public static final String invalidArtifactTypeError = "Artifact type override must be one of 'NO_ARTIFACTS', 'S3', ''";
    public static final String invalidArtifactsPackagingError = "Artifact packaging override must be one of 'NONE', 'ZIP', ''";
    public static final String invalidArtifactNamespaceTypeError = "Artifact namespace override must be one of 'NONE', 'BUILD_ID', ''";
    public static final String invalidTimeoutOverrideError = "Build timeout override must be a number between 5 and 480 (minutes)";
    public static final String invalidDefaultCredentialsError = "AWS credentials couldn't be loaded from the default provider chain";
    public static final String invalidRegionError = "Enter a valid AWS region";
    public static final String invalidProxyError = "Enter a valid proxy host and port (greater than zero)";
    public static final String invalidCredTypeError = "Invalid credentialsType option; must be 'jenkins' or 'keys'";
    public static final String invalidSecretKeyError = "awsSecretKey cannot be null";
    public static final String invalidCredentialsIdError = "Invalid credentials ID. Verify that the credentials are of type CodeBuildCredentials and are accessible in this project.";
    public static final String unableToGetJobFolder = "Unable to retrieve folder for this job.";
    public static final String basicAWSCredentials = "Using given AWS access and secret key for authorization";
    public static final String defaultChainCredentials = "Using credentials provided by the DefaultAWSCredentialsProviderChain for authorization";
    public static final String IAMRoleCredentials = "Authorizing with the IAM role defined in credentials ";
    public static final String invalidSourceTypeError = "Source type override must be one of 'CODECOMMIT', 'S3', 'GITHUB', 'GITHUB_ENTERPRISE', 'BITBUCKET'";
    public static final String invalidComputeTypeError = "Compute type override must be one of 'BUILD_GENERAL1_SMALL', 'BUILD_GENERAL1_MEDIUM', 'BUILD_GENERAL1_LARGE'";
    public static final String invalidEnvironmentTypeError = "Environment type override must be one of 'LINUX_CONTAINER', 'WINDOWS_CONTAINER'";
    public static final String invalidCacheTypeError = "Cache type override must be one of 'S3', 'NO_CACHE'";

    //CodeBuilder
    public static final String projectRequiredError = "CodeBuild project name is required";
    public static final String sourceControlTypeRequiredError = "Source control type is required and must be 'jenkins' or 'project'";

    public static String sanitize(final String s) {
        if(s == null) {
            return "";
        } else {
            return escapeSql(escapeHtml(s.trim()));
        }
    }

    public static Integer parseInt(String s) {
        if(s == null || s.isEmpty()) {
            return null;
        } else {
            return Integer.parseInt(s);
        }
    }

    public static String sanitizeYAML(final String s) {
        if(s == null) {
            return "";
        } else {
            return s.replace("\t", " ");
        }
    }

    //// Configuration-checking functions ////

    // CodeBuilder: if any of the parameters in CodeBuilder are bad, this will cause the build to end in failure in CodeBuilder.perform()

    public static String checkCodeBuilderConfig(CodeBuilder cb) {
        String projectName = cb.getParameterized(cb.getProjectName());
        if(projectName == null || projectName.isEmpty()) {
            return projectRequiredError;
        }
        String sourceControlType = cb.getParameterized(cb.getSourceControlType());
        if(!sourceControlType.equals(JenkinsSource.toString()) &&
            !sourceControlType.equals(ProjectSource.toString())) {
            return sourceControlTypeRequiredError;
        }
        return "";
    }

    // Returns empty string if configuration valid
    public static String checkCodeBuilderStartBuildOverridesConfig(CodeBuilder cb) {
        String artifactTypeOverride = cb.getParameterized(cb.getArtifactTypeOverride());
        if(!artifactTypeOverride.isEmpty()) {
            try {
                ArtifactsType.fromValue(artifactTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactTypeError;
            }
        }
        String artifactPackagingOverride = cb.getParameterized(cb.getArtifactPackagingOverride());
        if(!artifactPackagingOverride.isEmpty()) {
            try {
                ArtifactPackaging.fromValue(artifactPackagingOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactsPackagingError;
            }
        }

        String artifactNamespaceOverride = cb.getParameterized(cb.getArtifactNamespaceOverride());
        if(!artifactNamespaceOverride.isEmpty()) {
            try {
                ArtifactNamespace.fromValue(artifactNamespaceOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactNamespaceTypeError;
            }
        }

        String sourceTypeOverride = cb.getParameterized(cb.getSourceTypeOverride());
        if(!sourceTypeOverride.isEmpty()) {
            try {
                SourceType.fromValue(sourceTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidSourceTypeError;
            }
        }

        String computeTypeOverride = cb.getParameterized(cb.getComputeTypeOverride());
        if(!computeTypeOverride.isEmpty()) {
            try {
                ComputeType.fromValue(computeTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidComputeTypeError;
            }
        }

        String environmentTypeOverride = cb.getParameterized(cb.getEnvironmentTypeOverride());
        if(!environmentTypeOverride.isEmpty()) {
            try {
                EnvironmentType.fromValue(environmentTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidEnvironmentTypeError;
            }
        }

        String cacheTypeOverride = cb.getParameterized(cb.getCacheTypeOverride());
        if(!cacheTypeOverride.isEmpty()) {
            try {
                CacheType.fromValue(cacheTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidCacheTypeError;
            }
        }

        String timeout = cb.getParameterized(cb.getBuildTimeoutOverride());
        if(timeout != null && !timeout.isEmpty()) {
            Integer t;
            try {
                t = Integer.parseInt(timeout);
            } catch(NumberFormatException e) {
                return invalidTimeoutOverrideError;
            }
            if(t < 5 || t > 480) {
                return invalidTimeoutOverrideError;
            }
        }

        return "";
    }

    public static boolean envVariablesHaveRestrictedPrefix(Collection<EnvironmentVariable> envVariables) {
        for(EnvironmentVariable e: envVariables) {
            if(e.getName().startsWith("CODEBUILD_")) {
                return true;
            }
        }
        return false;
    }

    //CloudWatchMonitor
    public static boolean checkCloudWatchMonitorConfig(AWSLogsClient client) {
        if(client == null) {
            return false;
        }
        return true;
    }

    //S3DataManager
    public static void checkS3SourceUploaderConfig(FilePath workspace) throws Exception {
        if(workspace == null) {
            throw new Exception("Null workspace for project.");
        }
    }

    public static boolean checkSourceTypeS3(String sourceType) {
        if(sourceType != null && sourceType.equals("S3")) {
            return true;
        }
        return false;
    }

    public static boolean checkBucketIsVersioned(String bucketName, AWSClientFactory awsClientFactory) {
        final BucketVersioningConfiguration bucketVersioningConfig = awsClientFactory.getS3Client().getBucketVersioningConfiguration(bucketName);
        return bucketVersioningConfig.getStatus().equals(BucketVersioningConfiguration.ENABLED);
    }

    //AWSClientFactory
    public static void checkAWSClientFactoryJenkinsCredentialsConfig(String credentialsId) throws InvalidInputException {
        if(credentialsId == null || credentialsId.isEmpty()) {
            throw new InvalidInputException(invalidCredentialsIdError);
        }
    }

    public static void checkAWSClientFactoryRegionConfig(String region) throws InvalidInputException {
        if (region.isEmpty()) {
            throw new InvalidInputException(invalidRegionError);
        }
    }

    public static void checkAWSClientFactoryProxyConfig(String proxyHost, String proxyPort) throws InvalidInputException {
        if(proxyHost != null && !proxyHost.isEmpty()) {
            Integer proxyPortInt = Validation.parseInt(proxyPort);
            if(proxyPortInt != null && proxyPortInt < 0) {
                throw new InvalidInputException(invalidProxyError);
            }
        }
    }
}
