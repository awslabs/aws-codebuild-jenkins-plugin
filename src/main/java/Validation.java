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

import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import hudson.FilePath;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;

public class Validation {

    public static final String validSourceUrlPrefix = "https://";

    public static final String invalidIAMRoleError = "Enter a valid IAM Role ARN";
    public static final String invalidKeysError = "Enter valid AWS access and secret keys";
    public static final String invalidProxyError = "Enter a valid proxy host and port (greater than zero)";
    public static final String invalidCredTypeError = "Invalid credentialsType option";

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


    //// Configuration-checking functions ////

    // CodeBuilder: if any of the parameters in CodeBuilder are bad, this will cause the build to end in failure in CodeBuilder.perform()

    public static boolean checkCodeBuilderConfig(CodeBuilder cb) {
        if(cb.getProjectName() == null || cb.getProjectName().isEmpty()) {
            return false;
        }
        return true;
    }

    //CloudWatchMonitor
    public static boolean checkCloudWatchMonitorConfig(AWSLogsClient client) {
        if(client == null) {
            return false;
        }
        return true;
    }

    //S3DataManager
    public static void checkS3SourceUploaderConfig(String projectName, FilePath workspace) throws Exception {
        if(projectName == null || projectName.isEmpty()) {
            throw new Exception("Invalid project name.");
        }
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
    public static void checkAWSClientFactoryCredentialConfig(String awsAccessKey, String awsSecretKey) throws InvalidInputException {
        if(awsAccessKey == null || awsAccessKey.isEmpty() || awsSecretKey == null || awsSecretKey.isEmpty()) {
            throw new InvalidInputException(invalidKeysError);
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
