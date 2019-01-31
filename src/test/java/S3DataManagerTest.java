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

import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import enums.EncryptionAlgorithm;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.scm.SCM;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3DataManagerTest {

    private AmazonS3Client s3Client = mock(AmazonS3Client.class);
    private static final String mockWorkspaceDir = "/tmp/jenkins/workspace/proj";
    private FilePath testWorkSpace = new FilePath(new File(mockWorkspaceDir));
    private FilePath testZipSourceWorkspace = new FilePath(new File("/"));
    Map<String, String> s3ARNs = new HashMap<String, String>();
    private String s3InputBucketName = "Inputbucket";
    private String s3InputKeyName = "InputKey";
    private String sseAlgorithm = EncryptionAlgorithm.AES256.toString();
    private String localSourcePath = "";
    private String workspaceSubdir = "";

    AbstractBuild build = mock(AbstractBuild.class);
    BuildListener listener = mock(BuildListener.class);

    //mock console log
    private ByteArrayOutputStream log;

    @Before
    public void setUp() throws IOException, InterruptedException {
        //set the CodeBuilder instance to write its messages to the log here so
        //we can read what it prints.
        log = new ByteArrayOutputStream();
        PrintStream p = new PrintStream(log);
        when(listener.getLogger()).thenReturn(p);

        clearTestDirectories();
        testWorkSpace.mkdirs();
    }

    private S3DataManager createDefault() {
        return new S3DataManager(s3Client, s3InputBucketName, s3InputKeyName, sseAlgorithm, localSourcePath, workspaceSubdir);
    }

    //creates S3DataManager with parameters that won't throw a FileNotFoundException for below tests.
    private S3DataManager createDefaultSource(String localSourcePath, String workspaceSubdir) {
        this.s3ARNs.put("main", "ARN1/bucket/thing.zip"); //put one item in s3ARNs so exception doesn't happen.

        PutObjectResult mockedResponse = new PutObjectResult();
        mockedResponse.setVersionId("some-version-id");
        when(s3Client.putObject(any(PutObjectRequest.class))).thenReturn(mockedResponse);
        return new S3DataManager(s3Client, s3InputBucketName, s3InputKeyName, sseAlgorithm, localSourcePath, workspaceSubdir);
    }

    private void clearTestDirectories() throws IOException {
        File jenkinsDir = new File(mockWorkspaceDir);
        if(jenkinsDir.exists()) {
            FileUtils.cleanDirectory(jenkinsDir);
        }

        File zip = new File("/tmp/source.zip");
        if(zip.exists()) {
            FileUtils.deleteQuietly(zip);
        }

        File unzipFolder = new File("/tmp/folder");
        if(unzipFolder.exists()) {
            FileUtils.cleanDirectory(unzipFolder);
        }

        File src = new File("/tmp/source");
        if(src.exists()) {
            if(src.isDirectory()) {
                FileUtils.cleanDirectory(src);
            } else {
                FileUtils.deleteQuietly(src);
            }
        }
        src.mkdir();
    }

    @Test
    public void testNullConfig() {
        try {
            new S3DataManager(null, null, null, null, null, null).uploadSourceToS3(listener, testWorkSpace);
        } catch (InvalidInputException e) {
            assertEquals(e.getErrorMessage(), Validation.invalidSourceUploaderNullS3ClientError);
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testUploadSource() throws Exception {
        ArgumentCaptor<PutObjectRequest> savedPutObjectRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        UploadToS3Output result = createDefaultSource("", "").uploadSourceToS3(listener, testWorkSpace);
        assertEquals(result.getSourceLocation(), s3InputBucketName + "/" + s3InputKeyName);

        verify(s3Client).putObject(savedPutObjectRequest.capture());
        assertEquals(savedPutObjectRequest.getValue().getBucketName(), s3InputBucketName);
        assertEquals(savedPutObjectRequest.getValue().getKey(), s3InputKeyName);
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getSSEAlgorithm(), sseAlgorithm);
    }

    @Test
    public void testUploadSourceSubdir() throws Exception {
        File subdir = new File(mockWorkspaceDir + "/subdir");
        subdir.mkdirs();

        ArgumentCaptor<PutObjectRequest> savedPutObjectRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        UploadToS3Output result = createDefaultSource("", "subdir").uploadSourceToS3(listener, testWorkSpace);
        assertEquals(result.getSourceLocation(), s3InputBucketName + "/" + s3InputKeyName);

        verify(s3Client).putObject(savedPutObjectRequest.capture());
        assertEquals(savedPutObjectRequest.getValue().getBucketName(), s3InputBucketName);
        assertEquals(savedPutObjectRequest.getValue().getKey(), s3InputKeyName);
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getSSEAlgorithm(), sseAlgorithm);
    }

    @Test
    public void testUploadSourceNonexistentSubdir() {
        try {
            createDefaultSource("", "non-existent-subdir").uploadSourceToS3(listener, testWorkSpace);
        } catch (IOException e) {
            assert(e.getLocalizedMessage().contains("Empty or invalid source directory: " + mockWorkspaceDir + "/non-existent-subdir"));
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testUploadSourceSubdirAndLocalSourcePath() {
        try {
            createDefaultSource("source.zip", "subdir").uploadSourceToS3(listener, testWorkSpace);
        } catch (InvalidInputException e) {
            assertEquals(e.getErrorMessage(), Validation.invalidSourceUploaderConfigError);
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testUploadLocalNonexistentSource() {
        try {
            createDefaultSource("non-existent-file", "").uploadSourceToS3(listener, testWorkSpace);
        } catch(IOException e) {
            assert(e.getMessage().contains("non-existent-file (No such file or directory)"));
        } catch(Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testUploadLocalSource() throws Exception {
        File file = new File(mockWorkspaceDir + "/source-file");
        FileUtils.write(file, "contents");

        ArgumentCaptor<PutObjectRequest> savedPutObjectRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        UploadToS3Output result = createDefaultSource(file.getPath(), "").uploadSourceToS3(listener, testWorkSpace);
        assertEquals(result.getSourceLocation(), s3InputBucketName + "/" + s3InputKeyName);

        verify(s3Client).putObject(savedPutObjectRequest.capture());
        assertEquals(savedPutObjectRequest.getValue().getBucketName(), s3InputBucketName);
        assertEquals(savedPutObjectRequest.getValue().getKey(), s3InputKeyName);
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getContentMD5(), ZipSourceCallable.getZipMD5(file));
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getContentLength(), file.length());
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getSSEAlgorithm(), sseAlgorithm);
    }

    @Test
    public void testUploadLocalSourceWithNoSSEAlgorithm() throws Exception {
        File file = new File(mockWorkspaceDir + "/source-file");
        FileUtils.write(file, "contents");

        PutObjectResult mockedResponse = new PutObjectResult();
        mockedResponse.setVersionId("some-version-id");
        when(s3Client.putObject(any(PutObjectRequest.class))).thenReturn(mockedResponse);
        S3DataManager d = new S3DataManager(s3Client, s3InputBucketName, s3InputKeyName, "", file.getPath(), "");

        ArgumentCaptor<PutObjectRequest> savedPutObjectRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        UploadToS3Output result = d.uploadSourceToS3(listener, testWorkSpace);
        assertEquals(result.getSourceLocation(), s3InputBucketName + "/" + s3InputKeyName);

        verify(s3Client).putObject(savedPutObjectRequest.capture());
        assertEquals(savedPutObjectRequest.getValue().getBucketName(), s3InputBucketName);
        assertEquals(savedPutObjectRequest.getValue().getKey(), s3InputKeyName);
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getContentMD5(), ZipSourceCallable.getZipMD5(file));
        assertEquals(savedPutObjectRequest.getValue().getMetadata().getContentLength(), file.length());
        assertNull(savedPutObjectRequest.getValue().getMetadata().getSSEAlgorithm());
    }


    @Test
    public void testZipSourceEmptyDir() throws Exception {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        assertFalse(z.isValidZipFile()); //checks that zipfile is empty
    }

    @Test
    public void testZipSourceBuildSpec() throws Exception {
        String buildSpecName = "Buildspec.yml";
        File buildSpec = new File("/tmp/source/" + buildSpecName);
        String buildSpecContents = "yo\n";
        FileUtils.write(buildSpec, buildSpecContents);

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        assertTrue(unzipFolder.list().length == 1);
        assertEquals(buildSpecName, unzipFolder.list()[0]);
        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        assertTrue(FileUtils.readFileToString(extractedBuildSpec).equals(buildSpecContents));
    }

    @Test
    public void testZipSourceOneDirEmpty() throws Exception {
        String buildSpecName = "Buildspec.yml";
        File buildSpec = new File("/tmp/source/" + buildSpecName);
        String buildSpecContents = "yo\n";
        FileUtils.write(buildSpec, buildSpecContents);
        File sourceDir = new File("/tmp/source/src");
        sourceDir.mkdir();

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(buildSpecName));
        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        assertTrue(FileUtils.readFileToString(extractedBuildSpec).equals(buildSpecContents));
    }

    @Test
    public void testZipSourceOneDir() throws Exception {
        String buildSpecName = "Buildspec.yml";
        File buildSpec = new File("/tmp/source/" + buildSpecName);
        String buildSpecContents = "yo\n";
        FileUtils.write(buildSpec, buildSpecContents);
        File sourceDir = new File("/tmp/source/src");
        sourceDir.mkdir();
        String srcFileName = "/tmp/source/src/file.java";
        File srcFile = new File(srcFileName);
        String srcFileContents = "int i = 1;";
        FileUtils.write(srcFile, srcFileContents);

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        String[] fileList = unzipFolder.list();
        assertNotNull(fileList);
        Arrays.sort(fileList);

        assertTrue(fileList.length == 2);
        assertEquals(fileList[0], buildSpecName);
        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        assertEquals(FileUtils.readFileToString(extractedBuildSpec), buildSpecContents);

        String s = fileList[1];
        assertEquals(fileList[1], "src");
        File extractedSrcFile = new File(unzipFolder.getPath() + "/src/file.java");
        assertTrue(extractedSrcFile.exists());
        assertEquals(FileUtils.readFileToString(extractedSrcFile), srcFileContents);
    }

    @Test
    public void testZipSourceOneDirMultipleFiles() throws Exception {
        String buildSpecName = "buildspec.yml";
        String rootFileName = "pom.xml";
        String sourceDirName = "src";
        String srcFileName = "file.java";
        String srcFile2Name = "util.java";

        File buildSpec = new File("/tmp/source/" + buildSpecName);
        File rootFile = new File("/tmp/source/" + rootFileName);
        File sourceDir = new File("/tmp/source/" + sourceDirName);
        sourceDir.mkdir();
        File srcFile = new File("/tmp/source/src/" + srcFileName);
        File srcFile2 = new File("/tmp/source/src/" + srcFile2Name);

        String rootFileContents = "<plugin>codebuild</plugin>";
        String buildSpecContents = "Hello!!!!!";
        String srcFileContents = "int i = 1;";
        String srcFile2Contents = "util() { ; }";

        FileUtils.write(buildSpec, buildSpecContents);
        FileUtils.write(rootFile, rootFileContents);
        FileUtils.write(srcFile, srcFileContents);
        FileUtils.write(srcFile2, srcFile2Contents);

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        assertTrue(unzipFolder.list().length == 3);
        File srcFolder = new File("/tmp/folder/src/");
        assertTrue(srcFolder.list().length == 2);
        List<String> files = Arrays.asList(unzipFolder.list());
        assertTrue(files.contains(buildSpecName));
        assertTrue(files.contains(rootFileName));
        assertTrue(files.contains(sourceDirName));

        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        File extractedRootFile = new File(unzipFolder.getPath() + "/" + rootFileName);
        File extractedSrcFile = new File(unzipFolder.getPath() + "/src/" + srcFileName);
        File extractedSrcFile2 = new File(unzipFolder.getPath() + "/src/" + srcFile2Name);
        assertTrue(FileUtils.readFileToString(extractedBuildSpec).equals(buildSpecContents));
        assertTrue(FileUtils.readFileToString(extractedRootFile).equals(rootFileContents));
        assertTrue(FileUtils.readFileToString(extractedSrcFile).equals(srcFileContents));
        assertTrue(FileUtils.readFileToString(extractedSrcFile2).equals(srcFile2Contents));
    }

    @Test
    public void testZipSourceDoubleDirMultipleHugeFiles() throws Exception {
        String buildSpecName = "buildspec.yml";
        String rootFileName = "pom.xml";
        String sourceDirName = "src";
        String nestedDirName = "dir";
        String srcFileName = "file.java";
        String srcFile2Name = "util.java";

        File buildSpec = new File("/tmp/source/" + buildSpecName);
        File rootFile = new File("/tmp/source/" + rootFileName);
        File sourceDir = new File("/tmp/source/" + sourceDirName);
        sourceDir.mkdir();
        File nestedDir = new File("/tmp/source/" + sourceDirName + "/" + nestedDirName);
        nestedDir.mkdir();
        File srcFile = new File("/tmp/source/src/" + srcFileName);
        File srcFile2 = new File("/tmp/source/src/dir/" + srcFile2Name);

        StringBuilder rootFileContents = new StringBuilder();
        for(int i = 0; i < 1000; i++) {
            rootFileContents.append("word\n");
        }
        String buildSpecContents = "Hello!!!!!";
        String srcFileContents = "int i = 1;";
        StringBuilder srcFile2Contents = new StringBuilder();
        for(int i = 0; i < 2000; i++) {
            srcFile2Contents.append("function()");
        }

        FileUtils.write(buildSpec, buildSpecContents);
        FileUtils.write(rootFile, rootFileContents.toString());
        FileUtils.write(srcFile, srcFileContents);
        FileUtils.write(srcFile2, srcFile2Contents.toString());

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        assertTrue(unzipFolder.list().length == 3);
        File srcFolder = new File("/tmp/folder/src/");
        assertTrue(srcFolder.list().length == 2);
        File nestedFolder = new  File("/tmp/folder/src/dir/");
        assertTrue(nestedFolder.list().length == 1);
        List<String> files = Arrays.asList(unzipFolder.list());
        assertTrue(files.contains(buildSpecName));
        assertTrue(files.contains(rootFileName));
        assertTrue(files.contains(sourceDirName));
        assertTrue(nestedFolder.list()[0].equals(srcFile2Name));

        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        File extractedRootFile = new File(unzipFolder.getPath() + "/" + rootFileName);
        File extractedSrcFile = new File(unzipFolder.getPath() + "/src/" + srcFileName);
        File extractedSrcFile2 = new File(unzipFolder.getPath() + "/src/dir/" + srcFile2Name);
        assertTrue(FileUtils.readFileToString(extractedBuildSpec).equals(buildSpecContents));
        assertTrue(FileUtils.readFileToString(extractedRootFile).equals(rootFileContents.toString()));
        assertTrue(FileUtils.readFileToString(extractedSrcFile).equals(srcFileContents));
        assertTrue(FileUtils.readFileToString(extractedSrcFile2).equals(srcFile2Contents.toString()));
    }

    @Test
    public void testZipSourceMultipleNestedDirs() throws Exception {
        String buildSpecName = "buildspec.yml";
        String dir1Name = "dir1";
        String dir2Name = "dir2";
        String dir3Name = "dir3";
        String dir4Name = "dir4";
        String dir5Name = "dir5";
        String nestedFile4Name = "file.txt";
        String nestedFile5Name = "log.txt";

        File buildSpec = new File("/tmp/source/" + buildSpecName);
        File dir1 = new File("/tmp/source/" + dir1Name); dir1.mkdir();
        File dir2 = new File("/tmp/source/" + dir1Name + "/" + dir2Name); dir2.mkdir();
        File dir3 = new File("/tmp/source/" + dir1Name + "/" + dir2Name + "/" + dir3Name); dir3.mkdir();
        File dir4 = new File("/tmp/source/dir1/dir2/dir3/" + dir4Name); dir4.mkdir();
        File dir5 = new File("/tmp/source/dir1/dir2/dir3/" + dir5Name); dir5.mkdir();
        File file4 = new File("/tmp/source/dir1/dir2/dir3/dir4/" + nestedFile4Name);
        File file5 = new File("/tmp/source/dir1/dir2/dir3/dir5/" + nestedFile5Name);

        String buildSpecContents = "Hello!!!!!";
        String nestedFile4Contents = "words";
        String nestedFile5Contents = "logfile";
        FileUtils.write(buildSpec, buildSpecContents);
        FileUtils.write(file4, nestedFile4Contents);
        FileUtils.write(file5, nestedFile5Contents);

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());

        unzipFolder = new File("/tmp/folder/" + dir1Name);
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(dir2Name));

        unzipFolder = new File("/tmp/folder/" + dir1Name + "/" + dir2Name);
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(dir3Name));

        unzipFolder = new File("/tmp/folder/dir1/dir2/dir3/");
        assertTrue(unzipFolder.list().length == 2);

        unzipFolder = new File("/tmp/folder/dir1/dir2/dir3/dir4/");
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(nestedFile4Name));

        unzipFolder = new File("/tmp/folder/dir1/dir2/dir3/dir5/");
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(nestedFile5Name));
    }

    @Test
    public void testZipSourceHugeFile() throws Exception {
        String buildSpecName = "buildspec.yml";
        File buildSpec = new File("/tmp/source/" + buildSpecName);

        StringBuilder contents = new StringBuilder();

        for(int i = 0; i < 50000; i++) {
            contents.append("123234345456567678456234");
        }

        FileUtils.write(buildSpec, contents.toString());

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream("/tmp/source.zip"));
        ZipSourceCallable.zipSource(testZipSourceWorkspace, "/tmp/source/", out, "/tmp/source/");
        out.close();

        File zip = new File("/tmp/source.zip");
        assertTrue(zip.exists());

        File unzipFolder = new File("/tmp/folder/");
        unzipFolder.mkdir();
        ZipFile z = new ZipFile(zip.getPath());
        z.extractAll(unzipFolder.getPath());
        assertTrue(unzipFolder.list().length == 1);
        assertTrue(unzipFolder.list()[0].equals(buildSpecName));

        File extractedBuildSpec = new File(unzipFolder.getPath() + "/" + buildSpecName);
        assertTrue(FileUtils.readFileToString(extractedBuildSpec).equals(contents.toString()));
    }

    @Test
    public void testTrimPrefixBaseWithTrailingSlash() {
        String prefixWithSlash = FilenameUtils.separatorsToSystem("/tmp/dir/");  // "/tmp/dir/" in Linux, "\tmp\dir\" in Windows.
        String path = FilenameUtils.separatorsToSystem("/tmp/dir/folder/file.txt");

        assertEquals(FilenameUtils.separatorsToSystem("folder/file.txt"), ZipSourceCallable.trimPrefix(path, prefixWithSlash));
    }

    @Test
    public void testGetRelativePathStringBaseDirWithoutTrailingSlash() {
        String prefixNoSlash = FilenameUtils.separatorsToSystem("/tmp/dir"); // "/tmp/dir" in Linux, "\tmp\dir" in Windows.
        String path = FilenameUtils.separatorsToSystem("/tmp/dir/folder/file.txt");

        assertEquals(FilenameUtils.separatorsToSystem("folder/file.txt"), ZipSourceCallable.trimPrefix(path, prefixNoSlash));
    }
}
