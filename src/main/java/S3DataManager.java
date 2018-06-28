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
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import jenkins.MasterToSlaveFileCallable;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.CharEncoding;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

    // Clones, zips, and uploads the source code specified in the Source Code Management pane in the project configuration.
    // The upload bucket used is this.s3InputBucket and the name of the zip file is this.s3InputKey.
    public UploadToS3Output uploadSourceToS3(TaskListener listener, FilePath workspace) throws Exception {
        Validation.checkS3SourceUploaderConfig(workspace);

        String zipFileName = this.s3InputKey;
        String sourceFilePath = workspace.getRemote();
        String zipFilePath = sourceFilePath.substring(0, sourceFilePath.lastIndexOf(File.separator)+1) + UUID.randomUUID().toString() + "-" + zipFileName;
        FilePath jenkinsZipFile = new FilePath(workspace, zipFilePath);

        String zipFileMD5 = jenkinsZipFile.act(new ZipSourceCallable(workspace));

        // Add MD5 checksum as S3 Object metadata
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentMD5(zipFileMD5);
        objectMetadata.setContentLength(jenkinsZipFile.length());
        if(!sseAlgorithm.isEmpty()) {
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
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
}
