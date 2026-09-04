/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import hudson.FilePath;
import hudson.model.BuildListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.codebuild.model.Build;
import software.amazon.awssdk.services.codebuild.model.BuildArtifacts;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.codebuild.model.ProjectEnvironment;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

public class S3DownloaderTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final File tmpWorkspaceFile = new File("/tmp/testDir");
    private final FilePath testWorkSpace = new FilePath(tmpWorkspaceFile);
    private final File relativeWorkspaceFile = new File("/tmp/testDir/../");
    private final FilePath relativeWorkSpace = new FilePath(relativeWorkspaceFile);
    private final BuildArtifacts emptyBuildArtifacts = BuildArtifacts.builder().build();
    private final BuildArtifacts buildArtifacts = BuildArtifacts.builder().location("arn:aws:s3:::bucketName/A/log.txt").build();
    private final BuildArtifacts zippedBuildArtifacts = BuildArtifacts.builder().sha256sum("dummy").location("arn:aws:s3:::bucketName/A/buildOutput").build();
    private BuildListener listener = mock(BuildListener.class);
    private Build build;

    //mock console log
    private ByteArrayOutputStream log;

    @Before
    public void setUp() throws IOException, InterruptedException {
        //set the CodeBuilder instance to write its messages to the log here so
        //we can read what it prints.
        log = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(log);
        when(listener.getLogger()).thenReturn(p);
        // v1 -> v2: TransferManager.downloadDirectory is replaced by a ListObjectsV2 + GetObject
        // loop, so stub ListObjectsV2 to return a single object for the directory-download paths.
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("A/log.txt").build())
                        .isTruncated(false)
                        .build());
    }

    @After
    public void cleanTmpDirectories() {
        // Delete tmp workspace directory
        File dir = new File(tmpWorkspaceFile.getPath());
        if(dir.exists()) {
            try {
                FileUtils.forceDelete(dir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Delete relative workspace directory
        File relativeDir = new File(relativeWorkspaceFile.getPath());
        if(dir.exists()) {
            try {
                FileUtils.forceDelete(relativeDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<BuildArtifacts> getSecondaryArtifacts() {
        return new ArrayList<BuildArtifacts>() {
            {
                add(BuildArtifacts.builder().location("arn:aws:s3:::bucketName/B/log1.txt").build());
                add(BuildArtifacts.builder().location("arn:aws:s3:::bucketName/B/log2.txt").build());
            }
        };
    }

    private S3Downloader s3Downloader() {
        return new S3Downloader(s3Client);
    }

    @Test
    public void testNullConfig() {
        try {
            new S3Downloader(s3Client).downloadBuildArtifacts(listener, build, null);
        } catch (InvalidInputException e) {
            assertEquals(e.getMessage(), CodeBuilderValidation.buildInstanceRequiredError);
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyBuildArtifactLocation() {
        build = Build.builder().artifacts(emptyBuildArtifacts).build();
        ProjectEnvironment pv = build.environment();
        try {
            // downloadBuildArtifacts should gracefully handle empty artifacts
            s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testNullBuildArtifactLocation() {
        build = Build.builder().build();
        try {
            // downloadBuildArtifacts should gracefully handle null artifacts
            s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testDownloadArtifactDirectory() throws Exception {
        build = Build.builder().artifacts(buildArtifacts).build();
        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        // Directory download: one ListObjectsV2 followed by one GetObject for the single listed object.
        verify(s3Client, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
    }

    @Test
    public void testDownloadSecondaryArtifacts() throws Exception {
        build = Build.builder().secondaryArtifacts(getSecondaryArtifacts()).build();
        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
    }

    @Test
    public void testZippedArtifact() throws Exception {
        build = Build.builder().artifacts(zippedBuildArtifacts).build();
        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        // Single-file download (sha256sum present): one GetObject, no directory listing.
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));

        // Verify download directory is created
        String artifactLocation = Utils.getS3KeyFromObjectArn(build.artifacts().location());
        File file = new File(tmpWorkspaceFile.getPath() + File.separatorChar + artifactLocation);
        File parentDir = file.getParentFile();

        Assert.assertTrue(parentDir.exists());
        Assert.assertTrue(parentDir.isDirectory());
    }

    @Test
    public void testZippedArtifactWithRelativePath() throws Exception {
        build = Build.builder().artifacts(zippedBuildArtifacts).build();
        s3Downloader().downloadBuildArtifacts(listener, build, relativeWorkSpace.getRemote());

        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(s3Client, never()).listObjectsV2(any(ListObjectsV2Request.class));

        // Verify download directory is created
        String artifactLocation = Utils.getS3KeyFromObjectArn(build.artifacts().location());
        File file = new File(relativeWorkspaceFile.getCanonicalPath() + File.separatorChar + artifactLocation);
        File parentDir = file.getParentFile();

        Assert.assertTrue(parentDir.exists());
        Assert.assertTrue(parentDir.isDirectory());
    }

    @Test
    public void testZipSlipKeyRejected() throws Exception {
        // Fix #6: a crafted object key that escapes the destination directory must be rejected
        // and never downloaded (path traversal / zip-slip).
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("../evil.txt").build())
                        .isTruncated(false)
                        .build());
        build = Build.builder().artifacts(buildArtifacts).build(); // directory-download path
        File escaped = new File(tmpWorkspaceFile.getParentFile(), "evil.txt");
        if (escaped.exists()) {
            FileUtils.deleteQuietly(escaped);
        }

        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(s3Client, never()).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        assertFalse("zip-slip artifact must not be created outside the destination root", escaped.exists());
        assertTrue("rejection must be logged: " + log.toString(),
                log.toString().contains("escapes destination directory"));
    }

    @Test
    public void testNormalNestedKeyLandsUnderRoot() throws Exception {
        // Fix #6 (happy-path guard): a normal nested key still downloads under the root.
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("sub/dir/ok.txt").build())
                        .isTruncated(false)
                        .build());
        build = Build.builder().artifacts(buildArtifacts).build();

        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        File landed = new File(tmpWorkspaceFile, "sub/dir/ok.txt");
        Assert.assertTrue(landed.getParentFile().exists());
    }

    @Test
    public void testDirectoryDownloadSkipsFolderPlaceholderKeys() throws Exception {
        // v1 parity: TransferManager.downloadDirectory skipped S3 console "folder" placeholder
        // keys (ending in '/', size 0). If not skipped, such a key is created as a FILE and a
        // later object under that prefix (e.g. "dir/file.txt") fails mkdirs, aborting downloads.
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(
                                S3Object.builder().key("dir/").size(0L).build(),
                                S3Object.builder().key("dir/file.txt").size(4L).build())
                        .isTruncated(false)
                        .build());
        // Materialize downloaded objects on disk so we can assert the resulting layout.
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenAnswer(invocation -> {
                    GetObjectRequest req = invocation.getArgument(0);
                    FileUtils.write(new File(tmpWorkspaceFile, req.key()), "data");
                    return null;
                });
        build = Build.builder().artifacts(buildArtifacts).build(); // directory-download path

        s3Downloader().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        // The placeholder "folder" key must never be fetched...
        verify(s3Client, never()).getObject(
                argThat((GetObjectRequest r) -> r != null && "dir/".equals(r.key())),
                any(ResponseTransformer.class));
        // ...and the real object under it must still be downloaded.
        verify(s3Client, times(1)).getObject(
                argThat((GetObjectRequest r) -> r != null && "dir/file.txt".equals(r.key())),
                any(ResponseTransformer.class));
        // root/dir must be a DIRECTORY containing file.txt, not a placeholder file.
        File dir = new File(tmpWorkspaceFile, "dir");
        assertTrue("root/dir must be a directory", dir.isDirectory());
        assertTrue("file.txt must exist under root/dir", new File(dir, "file.txt").exists());
    }
}
