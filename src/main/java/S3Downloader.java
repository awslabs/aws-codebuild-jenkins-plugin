/*
 *  Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import hudson.model.TaskListener;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.codebuild.model.Build;
import software.amazon.awssdk.services.codebuild.model.BuildArtifacts;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;

public class S3Downloader {

    private final S3Client s3Client;

    public S3Downloader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void downloadBuildArtifacts(TaskListener listener, Build build, String artifactRoot) {
        if (build == null) {
            throw InvalidInputException.builder().message(CodeBuilderValidation.buildInstanceRequiredError).build();
        }

        // Download primary artifacts
        download(listener, build.artifacts(), artifactRoot);

        // Download secondary artifacts
        if (build.secondaryArtifacts() != null) {
            for (BuildArtifacts buildArtifact : build.secondaryArtifacts()) {
                download(listener, buildArtifact, artifactRoot);
            }
        }
    }

    private void download(TaskListener listener, BuildArtifacts buildArtifact, String artifactRoot) {
        if (buildArtifact == null
                || buildArtifact.location() == null
                || buildArtifact.location().isEmpty()
                || artifactRoot == null) {
            return;
        }

        String s3Bucket = Utils.getS3BucketFromObjectArn(buildArtifact.location());
        String keyPrefix = Utils.getS3KeyFromObjectArn(buildArtifact.location());
        try {
            if (buildArtifact.sha256sum() != null && !buildArtifact.sha256sum().isEmpty()) {
                // Download single zip file
                File file = resolveSafeChild(artifactRoot, keyPrefix);
                LoggingHelper.log(listener, "Downloading artifact from location '" + buildArtifact.location() + "' to path:" + file.getAbsolutePath());
                Utils.ensureFileExists(file);
                downloadObject(s3Bucket, keyPrefix, file);
            } else {
                File file = new File(artifactRoot);
                LoggingHelper.log(listener, "Downloading artifact from location '" + buildArtifact.location() + "' to path:" + file.getAbsolutePath());
                downloadDirectory(s3Bucket, keyPrefix, artifactRoot);
            }
        } catch (SdkException e) {
            LoggingHelper.log(listener, "Download failed:" + e.getMessage());
        } catch (IOException e) {
            LoggingHelper.log(listener, e.getMessage());
        }
    }

    private void downloadDirectory(String s3Bucket, String keyPrefix, String artifactRoot) throws IOException {
        String continuationToken = null;
        do {
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Bucket)
                    .prefix(keyPrefix)
                    .continuationToken(continuationToken)
                    .build());
            for (S3Object object : listResponse.contents()) {
                if (object.key().endsWith("/")) {
                    continue;
                }
                File objectFile = resolveSafeChild(artifactRoot, object.key());
                Utils.ensureFileExists(objectFile);
                downloadObject(s3Bucket, object.key(), objectFile);
            }
            continuationToken = Boolean.TRUE.equals(listResponse.isTruncated()) ? listResponse.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    private static File resolveSafeChild(String artifactRoot, String key) throws IOException {
        File root = new File(artifactRoot);
        File child = new File(root, key);
        String rootCanonical = root.getCanonicalPath();
        String childCanonical = child.getCanonicalPath();
        if (!childCanonical.equals(rootCanonical)
                && !childCanonical.startsWith(rootCanonical + File.separator)) {
            throw new IOException("artifact key escapes destination directory: " + key);
        }
        return child;
    }

    private void downloadObject(String s3Bucket, String key, File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete existing placeholder file " + file.getAbsolutePath());
        }
        s3Client.getObject(GetObjectRequest.builder().bucket(s3Bucket).key(key).build(),
                ResponseTransformer.toFile(file.toPath()));
    }
}
