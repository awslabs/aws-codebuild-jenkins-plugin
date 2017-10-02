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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    /*
        Returns the bucket name given S3 source location informaton.
        The given string can be in ARN format or <bucket>/<key> format, so handle both.
     */
    public static String getS3BucketFromObjectArn(String s3ObjectString) {
        Matcher stringRegex = Pattern.compile("(arn:aws:s3:::)?([^/]+)/.*").matcher(s3ObjectString);
        stringRegex.find();
        return stringRegex.group(2);
    }

    public static String getS3KeyFromObjectArn(String s3ObjectArn) {
        return s3ObjectArn.substring(s3ObjectArn.indexOf('/') + 1, s3ObjectArn.length());
    }

    public static String formatStringWithEllipsis(String s, int length) {
        return s.substring(0, length) + "...";
    }
}
