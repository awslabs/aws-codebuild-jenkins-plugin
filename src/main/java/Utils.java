/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors. Copyright 2004-2011 Oracle Corporation. Copyright 2010 Srikanth Reddy Lingala.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

public class Utils {

    public static String getS3BucketFromObjectArn(String s3ObjectArn) {
        String bucketArn = s3ObjectArn.substring(0, s3ObjectArn.indexOf('/'));
        if (bucketArn.startsWith("arn:aws:s3:::")) {
            return bucketArn.replaceAll("arn:aws:s3:::", "");
        } else {
            throw new RuntimeException("Unable to extract S3 bucket from object ARN.");
        }
    }

    public static String getS3KeyFromObjectArn(String s3ObjectArn) {
        return s3ObjectArn.substring(s3ObjectArn.indexOf('/') + 1, s3ObjectArn.length());
    }
}
