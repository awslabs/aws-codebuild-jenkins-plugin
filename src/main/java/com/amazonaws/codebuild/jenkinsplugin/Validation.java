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
package com.amazonaws.codebuild.jenkinsplugin;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.*;
import com.amazonaws.services.codebuild.model.*;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import hudson.FilePath;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;

import static enums.SourceControlType.JenkinsSource;
import static enums.SourceControlType.ProjectSource;
import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;
import static org.apache.commons.lang.StringEscapeUtils.escapeSql;

public class Validation {

    //AWSClientFactory
    public static final String invalidDefaultCredentialsError = "AWS credentials couldn't be loaded from the default provider chain";
    public static final String invalidCredTypeError = "Invalid credentialsType option; must be 'jenkins' or 'keys'";
    public static final String invalidSecretKeyError = "awsSecretKey cannot be null";
    public static final String stepCredentials = "Using credentials provided by the Jenkins step context for authorization";

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

    public static AWSCredentialsProvider getBasicCredentialsOrDefaultChain(String accessKey, String secretKey) {
        return getBasicCredentialsOrDefaultChain(accessKey, secretKey, "");
    }

    public static AWSCredentialsProvider getBasicCredentialsOrDefaultChain(String accessKey, String secretKey, String awsSessionToken) {
        AWSCredentialsProvider result;
        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey) && StringUtils.isNotEmpty(awsSessionToken)) {
            result = new AWSStaticCredentialsProvider(new BasicSessionCredentials(accessKey, secretKey, awsSessionToken));
        }
        else if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
            result = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        } else {
            result = DefaultAWSCredentialsProviderChain.getInstance();
            try {
                result.getCredentials();
            } catch (SdkClientException e) {
                throw new InvalidInputException(invalidDefaultCredentialsError);
            }
        }
        return result;
    }

}
