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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (C) 2015 The Project Lombok Authors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RequiredArgsConstructor
public class S3DataManager {

    private final String projectName;
    private final FilePath workspace;
    private final String buildDisplayName; //takes the form "<project name> #<build number>
    private final AmazonS3Client s3Client;
    private final String s3InputBucket;
    private final String s3InputKey;

    @Setter
    private OutputStream writer;

    public static final String zipSourceError = "zipSource usage: prefixToTrim must be contained in the given directory.";

    // Clones, zips, and uploads the source code specified in the Source Code Management pane in the project configuration.
    // The upload bucket used is this.s3InputBucket and the name of the zip file is source.zip.
    // @return: the s3 bucket where the zip containing the source can be found
    //  (takes the form <this.s3InputBucket>/<project name>-source.zip).
    public UploadToS3Output uploadSourceToS3(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception {
        Validation.checkS3SourceUploaderConfig(projectName, workspace);

        String localfileName = this.projectName + "-" + "source.zip";
        String sourceFilePath = workspace.getRemote();
        String zipFilePath = sourceFilePath.substring(0, sourceFilePath.lastIndexOf(File.separator)) + File.separator + localfileName;
        File zipFile = new File(zipFilePath);

        if (!zipFile.getParentFile().exists()) {
            boolean dirMade = zipFile.getParentFile().mkdirs();
            if (!dirMade) {
                throw new Exception("Unable to create directory: " + zipFile.getParentFile().getAbsolutePath());
            }
        }

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFilePath));
        try {
            zipSource(sourceFilePath, out, sourceFilePath);
        } finally {
            out.close();
        }

        File sourceZipFile = new File(zipFilePath);
        PutObjectRequest putObjectRequest = new PutObjectRequest(s3InputBucket, s3InputKey, sourceZipFile);

        // Add MD5 checksum as S3 Object metadata
        String zipFileMD5;
        try (FileInputStream fis = new FileInputStream(zipFilePath)) {
            zipFileMD5 = new String(org.apache.commons.codec.binary.Base64.encodeBase64(DigestUtils.md5(fis)), "UTF-8");
        }
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentMD5(zipFileMD5);
        objectMetadata.setContentLength(sourceZipFile.length());
        putObjectRequest.setMetadata(objectMetadata);

        LoggingHelper.log(listener, "Uploading code to S3 at location " + putObjectRequest.getBucketName() + "/" + putObjectRequest.getKey() + ". MD5 checksum is " + zipFileMD5);
        PutObjectResult putObjectResult = s3Client.putObject(putObjectRequest);

        return new UploadToS3Output(putObjectRequest.getBucketName() + "/" + putObjectRequest.getKey(), putObjectResult.getVersionId());
    }


    // Recursively zips everything in the given directory into a zip file using the given ZipOutputStream.
    // @param directory: whose contents will be zipped.
    // @param out: ZipOutputStream that will write data to its zip file.
    // @param prefixToTrim: the prefix in directory that should be trimmed before zipping.
    //    Example:
    //     The given directory is /tmp/dir/folder/ which contains one file /tmp/dir/folder/file.txt
    //     The given prefixToTrim is /tmp/dir/
    //     Then the zip file created will expand into folder/file.txt
    //    Example:
    //     The given directory is /tmp/dir/folder/ which contains one file /tmp/dir/folder/file.txt
    //     The given prefixToTrim is /tmp/dir/folder
    //     Then the zip file created will expand into file.txt
    public static void zipSource(final String directory, final ZipOutputStream out, final String prefixToTrim) throws Exception {
        if (!Paths.get(directory).startsWith(Paths.get(prefixToTrim))) {
            throw new Exception(zipSourceError + "prefixToTrim: " + prefixToTrim + ", directory: "+ directory);
        }

        File dir = new File(directory);
        String[] dirFiles = dir.list();
        if (dirFiles == null) {
            throw new Exception("Invalid directory path provided: " + directory);
        }
        byte[] buffer = new byte[1024];
        int bytesRead;

        for (int i = 0; i < dirFiles.length; i++) {
            File f = new File(dir, dirFiles[i]);
            if (f.isDirectory()) {
                if(f.getName().equals(".git") == false) {
                  zipSource(f.getPath() + File.separator, out, prefixToTrim);
                }
            } else {
                FileInputStream inputStream = new FileInputStream(f);
                try {
                    String path = trimPrefix(f.getPath(), prefixToTrim);

                    ZipEntry entry = new ZipEntry(path);
                    out.putNextEntry(entry);
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                } finally {
                    inputStream.close();
                }
            }
        }
    }

    // Trim a directory prefix from a file path
    // @param path: file path.
    // @param prefixToTrim: the prefix in directory that should be trimmed before zipping.
    // @return path with no prefixToTrim
    //   Example:
    //     The given path is /tmp/dir/folder/file.txt
    //     The given prefixToTrim can be /tmp/dir/ or /tmp/dir
    //     Then the returned path string will be folder/file.txt.
    public static String trimPrefix(final String path, final String prefixToTrim) {
        return Paths.get(prefixToTrim).relativize(Paths.get(path)).toString();
    }
}
