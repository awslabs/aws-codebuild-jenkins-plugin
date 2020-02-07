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

import com.amazonaws.services.codebuild.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    @Test
    public void testExtractS3BucketValidWithPeriod() {
        String objectArn = "arn:aws:s3:::my-corporate-bucketexampleobject.png";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucketexampleobject.png", bucket);
    }

    @Test
    public void testExtractS3BucketValidWithPeriodAndNested() {
        String objectArn = "arn:aws:s3:::my-corporate-bucketexampleobject.png/test/dir/file.txt";
        String bucket = Utils.getS3BucketFromObjectArn(objectArn);
        assertEquals("my-corporate-bucketexampleobject.png", bucket);
    }

    @Test
    public void testExtractS3Key() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/exampleobject.png";
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("exampleobject.png", key);
    }

    @Test
    public void testExtractS3KeyWithNoKey() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket";
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("", key);
    }

    @Test
    public void testExtractS3KeyWithNoKeyWithTrailingSlash() {
        String objectArn = "arn:aws:s3:::my-corporate-bucket/";
        String key = Utils.getS3KeyFromObjectArn(objectArn);
        assertEquals("", key);
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

    @Test
    public void testParseSSVEmpty() {
        assert(Utils.parseDataList("", ProjectSourceVersion.class).isEmpty());
        assert(Utils.parseDataList(null, ProjectSourceVersion.class).isEmpty());
    }

    @Test
    public void testParseSSVInvalidKey() {
        try {
            Utils.parseDataList("[{\"sourceId\": \"a\", \"sourceVersion\": \"v\"}]", ProjectSourceVersion.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unrecognized field \"sourceId\""));
        }
    }

    @Test
    public void testParseSSVMalformedInput() {
        try {
            Utils.parseDataList("[{\"sourceIdentifier\": \"a\", ,\"sourceVersion\": \"v\"}]", ProjectSourceVersion.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unexpected character"));
        }
    }

    @Test
    public void testParseSSVMissingField() {
        List<ProjectSourceVersion> versions = Utils.parseDataList("[{\"sourceIdentifier\": \"a\"}]", ProjectSourceVersion.class);

        assertEquals(versions.size(), 1);
        assertEquals(versions.get(0).getSourceIdentifier(), "a");
        assertNull(versions.get(0).getSourceVersion());
    }

    @Test
    public void testParseSSVHappyCase() {
        List<ProjectSourceVersion> versions = Utils.parseDataList("[{\"sourceIdentifier\": \"a\", \"sourceVersion\": \"v\"}]", ProjectSourceVersion.class);

        assertEquals(versions.size(), 1);
        assertEquals(versions.get(0).getSourceIdentifier(), "a");
        assertEquals(versions.get(0).getSourceVersion(), "v");
    }

    @Test
    public void testParseMultipleSSVHappyCase() {
        List<ProjectSourceVersion> versions = Utils.parseDataList(
                "[{\"sourceIdentifier\": \"a\", \"sourceVersion\": \"v1\"},{\"sourceIdentifier\": \"b\", \"sourceVersion\": \"v2\"}]", ProjectSourceVersion.class);

        assertEquals(versions.size(), 2);
        assert(versions.contains(new ProjectSourceVersion().withSourceIdentifier("a").withSourceVersion("v1")));
        assert(versions.contains(new ProjectSourceVersion().withSourceIdentifier("b").withSourceVersion("v2")));
    }


    @Test
    public void testParseSSEmptyNull() {
        assert(Utils.parseDataList("", ProjectSource.class).isEmpty());
        assert(Utils.parseDataList(null, ProjectSource.class).isEmpty());
    }

    @Test
    public void testParseSSInvalidKey() {
        try {
            Utils.parseDataList("[{\"invalidField\": \"t\", \"location\": \"l\"}]", ProjectSource.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unrecognized field \"invalidField\""));
        }
    }

    @Test
    public void testParseSSMalformedInput() {
        try {
            Utils.parseDataList("[{\"type\": \"t\", ,\"location\": \"l\"}]", ProjectSource.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unexpected character"));
        }
    }

    @Test
    public void testParseSSInvalidBoolean() {
        try {
            Utils.parseDataList("[{\"reportBuildStatus\": \"t\"}]", ProjectSource.class);
        } catch (InvalidInputException e) {

            assert(e.getMessage().contains("Cannot deserialize value of type `java.lang.Boolean` from String \"t\""));
        }
    }

    @Test
    public void testParseSSMissingField() {
        List<ProjectSource> sources = Utils.parseDataList("[{\"type\": \"t\"}]", ProjectSource.class);

        assertEquals(sources.size(), 1);
        assertEquals(sources.get(0).getType(), "t");
        assertNull(sources.get(0).getAuth());
        assertNull(sources.get(0).getBuildspec());
        assertNull(sources.get(0).getGitCloneDepth());
        assertNull(sources.get(0).getInsecureSsl());
        assertNull(sources.get(0).getLocation());
        assertNull(sources.get(0).getReportBuildStatus());
        assertNull(sources.get(0).getSourceIdentifier());
    }

    @Test
    public void testParseSSHappyCase() {
        List<ProjectSource> sources = Utils.parseDataList(
                "[{\"type\": \"t\", " +
                  "\"location\": \"l\", " +
                  "\"gitCloneDepth\": \"1\", " +
                  "\"buildspec\": \"b\", " +
                  "\"reportBuildStatus\": \"true\", " +
                  "\"insecureSsl\": \"true\", " +
                  "\"sourceIdentifier\": \"i\", " +
                  "\"auth\": {\"type\": \"at\", \"resource\": \"ar\"} " +
                "}]", ProjectSource.class);

        assertEquals(sources.size(), 1);
        assertEquals(sources.get(0).getType(), "t");
        assertEquals(sources.get(0).getLocation(), "l");
        assert(sources.get(0).getGitCloneDepth() == 1);
        assertEquals(sources.get(0).getBuildspec(), "b");
        assertEquals(sources.get(0).getReportBuildStatus(), true);
        assertEquals(sources.get(0).getInsecureSsl(), true);
        assertEquals(sources.get(0).getSourceIdentifier(), "i");
        assertEquals(sources.get(0).getAuth(), new SourceAuth().withType("at").withResource("ar"));
    }

    @Test
    public void testParseMultipleSS() {
        List<ProjectSource> sources = Utils.parseDataList(
                "[{\"type\": \"t1\", \"location\": \"l1\"},{\"type\": \"t2\", \"location\": \"l2\"}]", ProjectSource.class);

        assertEquals(sources.size(), 2);
        assert(sources.contains(new ProjectSource().withType("t1").withLocation("l1")));
        assert(sources.contains(new ProjectSource().withType("t2").withLocation("l2")));
    }

    @Test
    public void testParseSAEmptyNull() {
        assert(Utils.parseDataList("", ProjectArtifacts.class).isEmpty());
        assert(Utils.parseDataList(null, ProjectArtifacts.class).isEmpty());
    }

    @Test
    public void testParseSAInvalidKey() {
        try {
            Utils.parseDataList("[{\"artifactId\": \"a\"}]", ProjectArtifacts.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unrecognized field \"artifactId\""));
        }
    }

    @Test
    public void testParseSAMalformedInput() {
        try {
            Utils.parseDataList("[{\"type\": \"a\", ,\"location\": \"v\"}]", ProjectArtifacts.class);
        } catch (InvalidInputException e) {
            assert(e.getMessage().contains("Unexpected character"));
        }
    }

    @Test
    public void testParseSAMissingField() {
        List<ProjectArtifacts> artifacts = Utils.parseDataList("[{\"type\": \"t\"}]", ProjectArtifacts.class);

        assertEquals(artifacts.size(), 1);
        assertEquals(artifacts.get(0).getType(), "t");
        assertNull(artifacts.get(0).getArtifactIdentifier());
        assertNull(artifacts.get(0).getEncryptionDisabled());
        assertNull(artifacts.get(0).getLocation());
        assertNull(artifacts.get(0).getName());
        assertNull(artifacts.get(0).getNamespaceType());
        assertNull(artifacts.get(0).getOverrideArtifactName());
        assertNull(artifacts.get(0).getPackaging());
        assertNull(artifacts.get(0).getPath());
    }

    @Test
    public void testParseSAInvalidBoolean() {
        try {
            Utils.parseDataList("[{\"overrideArtifactName\": \"t\"}]", ProjectArtifacts.class);
        } catch (InvalidInputException e) {

            assert(e.getMessage().contains("Cannot deserialize value of type `java.lang.Boolean` from String \"t\""));
        }
    }

    @Test
    public void testParseSAHappyCase() {
        List<ProjectArtifacts> artifacts = Utils.parseDataList(
                "[{\"type\": \"t\", " +
                  "\"location\": \"l\", " +
                  "\"path\": \"p\", " +
                  "\"namespaceType\": \"nt\", " +
                  "\"name\": \"n\", " +
                  "\"packaging\": \"p\", " +
                  "\"overrideArtifactName\": \"true\", " +
                  "\"encryptionDisabled\": \"true\", " +
                  "\"artifactIdentifier\": \"i\"" +
                "}]", ProjectArtifacts.class);

        assertEquals(artifacts.size(), 1);
        assertEquals(artifacts.get(0).getType(), "t");
        assertEquals(artifacts.get(0).getLocation(), "l");
        assertEquals(artifacts.get(0).getPath(), "p");
        assertEquals(artifacts.get(0).getNamespaceType(), "nt");
        assertEquals(artifacts.get(0).getName(), "n");
        assertEquals(artifacts.get(0).getPackaging(), "p");
        assertEquals(artifacts.get(0).getOverrideArtifactName(), true);
        assertEquals(artifacts.get(0).getEncryptionDisabled(), true);
        assertEquals(artifacts.get(0).getArtifactIdentifier(), "i");
    }

    @Test
    public void testParseMultipleSAHappyCase() {
        List<ProjectArtifacts> artifacts = Utils.parseDataList(
                "[{\"type\": \"t1\"},{\"type\": \"t2\"}]", ProjectArtifacts.class);

        assertEquals(artifacts.size(), 2);
        assert(artifacts.contains(new ProjectArtifacts().withType("t1")));
        assert(artifacts.contains(new ProjectArtifacts().withType("t2")));
    }

    @Test
    public void testDecodeJSON() {
        assertEquals(CodeBuilder.decodeJSON("abc"), "abc");
        assertEquals(CodeBuilder.decodeJSON("&amp;quot;"), "\"");
        assertEquals(CodeBuilder.decodeJSON("&amp;quot;&quot;"), "\"\"");
        assertEquals(CodeBuilder.decodeJSON("''a&amp;b&gt;c&lt;''"), "'a&b>c<'");
    }

}
