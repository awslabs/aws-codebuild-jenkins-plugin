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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

    @Test
    public void testExtractS3Bucket() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/exampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucket", bucket);
    }

    @Test
    public void testExtractS3BucketNoArnPrefix() {
        String objectArn = "my-corporate-bucket/exampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucket", bucket);
    }

    @Test(expected=RuntimeException.class)
    public void testExtractS3BucketEmpty() {
        String objectArn = "";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
    }

    @Test(expected=RuntimeException.class)
    public void testExtractS3BucketInvalid() {
        String objectArn = "arn:aws:s3:::my-corporate-bucketexampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
    }

    @Test
    public void testExtractS3Key() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/exampleobject.png";
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("exampleobject.png", key);
    }

    @Test
    public void testExtractS3KeyWithFolder() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/somefolder/exampleobject.png";
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("somefolder/exampleobject.png", key);
    }

    @Test
    public void testExtractS3BucketWithFolder() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/somefolder/exampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucket", bucket);
    }

    @Test
    public void testExtractWithMultipleFolder() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/somefolder/folder/sub/exampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucket", bucket);
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("somefolder/folder/sub/exampleobject.png", key);
    }
}
