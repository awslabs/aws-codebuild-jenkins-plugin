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
import hudson.scm.SCM;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.CharEncoding;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.codec.binary.Base64.encodeBase64;


@RequiredArgsConstructor
public class S3DataManager {

    private final AmazonS3Client s3Client;
    private final String s3InputBucket;
    private final String s3InputKey;
    private final String sseAlgorithm;

    public static final String zipSourceError = "zipSource usage: prefixToTrim must be contained in the given directory.";

    // Clones, zips, and uploads the source code specified in the Source Code Management pane in the project configuration.
    // The upload bucket used is this.s3InputBucket and the name of the zip file is this.s3InputKey.
    public UploadToS3Output uploadSourceToS3(TaskListener listener, FilePath workspace) throws Exception {
        Validation.checkS3SourceUploaderConfig(workspace);

        String zipFileName = this.s3InputKey;
        String sourceFilePath = workspace.getRemote();
        String zipFilePath = sourceFilePath.substring(0, sourceFilePath.lastIndexOf(File.separator)+1) + UUID.randomUUID().toString() + "-" + zipFileName;
        FilePath jenkinsZipFile = new FilePath(workspace, zipFilePath);

        try(OutputStream zipFileOutputStream = jenkinsZipFile.write()) {
            try(ZipOutputStream out = new ZipOutputStream(zipFileOutputStream)) {
                zipSource(workspace, sourceFilePath, out, sourceFilePath);
            }
        }

        // Add MD5 checksum as S3 Object metadata
        ObjectMetadata objectMetadata = new ObjectMetadata();
        String zipFileMD5;

        try(InputStream zipFileInputStream = jenkinsZipFile.read()) {
            zipFileMD5 = new String(encodeBase64(DigestUtils.md5(zipFileInputStream)), Charsets.UTF_8);
            objectMetadata.setContentMD5(zipFileMD5);
            objectMetadata.setContentLength(jenkinsZipFile.length());
            if(!sseAlgorithm.isEmpty()) {
                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
            }
        }

        PutObjectRequest putObjectRequest;
        PutObjectResult putObjectResult;

        try(InputStream zipFileInputStream = jenkinsZipFile.read()) {
            putObjectRequest = new PutObjectRequest(s3InputBucket, s3InputKey, zipFileInputStream, objectMetadata);
            putObjectRequest.setMetadata(objectMetadata);
            LoggingHelper.log(listener, "Uploading code to S3 at location " + putObjectRequest.getBucketName() + "/" + putObjectRequest.getKey() + ". MD5 checksum is " + zipFileMD5);
            putObjectResult = s3Client.putObject(putObjectRequest);
        } finally {
            jenkinsZipFile.delete();
        }

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
    public static void zipSource(FilePath workspace, final String directory, final ZipOutputStream out, final String prefixToTrim) throws Exception {
        if (!Paths.get(directory).startsWith(Paths.get(prefixToTrim))) {
            throw new Exception(zipSourceError + "prefixToTrim: " + prefixToTrim + ", directory: "+ directory);
        }

        FilePath dir = new FilePath(workspace, directory);
        List<FilePath> dirFiles = dir.list();
        if (dirFiles == null) {
            throw new Exception("Empty or invalid source directory: " + directory + ". Did you download any source as part of your build?");
        }
        byte[] buffer = new byte[1024];
        int bytesRead;

        for (int i = 0; i < dirFiles.size(); i++) {
            FilePath f = new FilePath(workspace, dirFiles.get(i).getRemote());
            if (f.isDirectory()) {
                zipSource(workspace, f.getRemote() + File.separator, out, prefixToTrim);
            } else {
                InputStream inputStream = f.read();
                try {
                    String path = trimPrefix(f.getRemote(), prefixToTrim);

                    if(path.startsWith(File.separator)) {
                    	path = path.substring(1, path.length());
                    }

                    // Zip files created on the windows file system will not unzip
                    // properly on unix systems. Without this change, no directory structure
                    // is built when unzipping.
                    path = path.replace(File.separator, "/");

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
