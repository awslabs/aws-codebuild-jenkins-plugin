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

import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.BuildArtifacts;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.codebuild.model.ProjectEnvironment;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.FilePath;
import hudson.model.BuildListener;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class S3DownloaderTest {

    private final AmazonS3Client s3Client = mock(AmazonS3Client.class);
    private final File tmpWorkspaceFile = new File("/tmp/testDir");
    private final FilePath testWorkSpace = new FilePath(tmpWorkspaceFile);
    private final File relativeWorkspaceFile = new File("/tmp/testDir/../");
    private final FilePath relativeWorkSpace = new FilePath(relativeWorkspaceFile);
    private final BuildArtifacts emptyBuildArtifacts = new BuildArtifacts();
    private final BuildArtifacts buildArtifacts = new BuildArtifacts().withLocation("arn:aws:s3:::bucketName/A/log.txt");
    private final BuildArtifacts zippedBuildArtifacts = new BuildArtifacts().withSha256sum("dummy").withLocation("arn:aws:s3:::bucketName/A/buildOutput");
    private BuildListener listener = mock(BuildListener.class);
    private TransferManager transferManager = mock(TransferManager.class);
    private Download mockDownload = mock(Download.class);
    private MultipleFileDownload multipleFileDownload = mock(MultipleFileDownload.class);
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
        when(transferManager.download(any(String.class), any(String.class), any(File.class))).thenReturn(mockDownload);
        when(transferManager.downloadDirectory(any(String.class), any(String.class), any(File.class))).thenReturn(multipleFileDownload);
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
                add(new BuildArtifacts().withLocation("arn:aws:s3:::bucketName/B/log1.txt"));
                add(new BuildArtifacts().withLocation("arn:aws:s3:::bucketName/B/log2.txt"));
            }
        };
    }

    private S3Downloader s3DownloaderWithMockTransferManager() {
        return new S3Downloader(s3Client, transferManager);
    }

    @Test
    public void testNullConfig() {
        try {
            new S3Downloader(s3Client).downloadBuildArtifacts(listener, build, null);
        } catch (InvalidInputException e) {
            assertEquals(e.getErrorMessage(), CodeBuilderValidation.buildInstanceRequiredError);
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testEmptyBuildArtifactLocation() {
        build = new Build().withArtifacts(emptyBuildArtifacts);
        ProjectEnvironment pv = build.getEnvironment();
        try {
            // downloadBuildArtifacts should gracefully handle empty artifacts
            s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testNullBuildArtifactLocation() {
        build = new Build();
        try {
            // downloadBuildArtifacts should gracefully handle null artifacts
            s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testDownloadArtifactDirectory() throws Exception {
        build = new Build().withArtifacts(buildArtifacts);
        s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(transferManager, times(1)).downloadDirectory(any(String.class), any(String.class), any(File.class));
        verify(multipleFileDownload, times(1)).waitForCompletion();
    }

    @Test
    public void testDownloadSecondaryArtifacts() throws Exception {
        build = new Build().withSecondaryArtifacts(getSecondaryArtifacts());
        s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(transferManager, times(2)).downloadDirectory(any(String.class), any(String.class), any(File.class));
        verify(multipleFileDownload, times(2)).waitForCompletion();
    }

    @Test
    public void testZippedArtifact() throws Exception {
        build = new Build().withArtifacts(zippedBuildArtifacts);
        s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, testWorkSpace.getRemote());

        verify(transferManager, times(1)).download(any(String.class), any(String.class), any(File.class));
        verify(mockDownload, times(1)).waitForCompletion();

        // Verify download directory is created
        String artifactLocation = Utils.getS3KeyFromObjectArn(build.getArtifacts().getLocation());
        File file = new File(tmpWorkspaceFile.getPath() + File.separatorChar + artifactLocation);
        File parentDir = file.getParentFile();

        Assert.assertTrue(parentDir.exists());
        Assert.assertTrue(parentDir.isDirectory());
    }

    @Test
    public void testZippedArtifactWithRelativePath() throws Exception {
        build = new Build().withArtifacts(zippedBuildArtifacts);
        s3DownloaderWithMockTransferManager().downloadBuildArtifacts(listener, build, relativeWorkSpace.getRemote());

        verify(transferManager, times(1)).download(any(String.class), any(String.class), any(File.class));
        verify(mockDownload, times(1)).waitForCompletion();

        // Verify download directory is created
        String artifactLocation = Utils.getS3KeyFromObjectArn(build.getArtifacts().getLocation());
        File file = new File(relativeWorkspaceFile.getCanonicalPath() + File.separatorChar + artifactLocation);
        File parentDir = file.getParentFile();

        Assert.assertTrue(parentDir.exists());
        Assert.assertTrue(parentDir.isDirectory());
    }
}
