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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.BuildArtifacts;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.Transfer;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;

public class S3Downloader {

    private final AmazonS3Client s3Client;
    private TransferManager transferManager;

    public S3Downloader(AmazonS3Client s3Client) {
        this.s3Client = s3Client;
        transferManager = TransferManagerBuilder.standard().withS3Client(s3Client).build();
    }

    @VisibleForTesting
    public S3Downloader(AmazonS3Client s3Client, TransferManager transferManager) {
        this.s3Client = s3Client;
        this.transferManager = transferManager;
    }

    public void downloadBuildArtifacts(TaskListener listener, Build build, String artifactRoot) {
        if (build == null) {
            throw new InvalidInputException(CodeBuilderValidation.buildInstanceRequiredError);
        }

        // Download primary artifacts
        download(listener, build.getArtifacts(), artifactRoot);

        // Download secondary artifacts
        if (build.getSecondaryArtifacts() != null) {
            for (BuildArtifacts buildArtifact : build.getSecondaryArtifacts()) {
                download(listener, buildArtifact, artifactRoot);
            }
        }
    }

    private void download(TaskListener listener, BuildArtifacts buildArtifact, String artifactRoot) {
        if (buildArtifact == null
                || buildArtifact.getLocation() == null
                || buildArtifact.getLocation().isEmpty()
                || artifactRoot == null) {
            return;
        }

        String s3Bucket = Utils.getS3BucketFromObjectArn(buildArtifact.getLocation());
        String keyPrefix = Utils.getS3KeyFromObjectArn(buildArtifact.getLocation());
        Transfer transfer;
        try {
            if (buildArtifact.getSha256sum() != null && !buildArtifact.getSha256sum().isEmpty()) {
                // Download single zip file
                File file = new File(artifactRoot + File.separatorChar + keyPrefix);
                LoggingHelper.log(listener, "Downloading artifact from location '" + buildArtifact.getLocation() + "' to path:" + file.getAbsolutePath());
                Utils.ensureFileExists(file);
                transfer = transferManager.download(s3Bucket, keyPrefix, file);
            } else {
                // Download entire directory content
                File file = new File(artifactRoot);
                LoggingHelper.log(listener, "Downloading artifact from location '" + buildArtifact.getLocation() + "' to path:" + file.getAbsolutePath());
                transfer = transferManager.downloadDirectory(s3Bucket, keyPrefix, file);
            }
            transfer.waitForCompletion();
        } catch (AmazonServiceException e) {
            LoggingHelper.log(listener, "Download failed:" + e.getMessage());
        } catch (InterruptedException e) {
            LoggingHelper.log(listener, "Download failed:" + e.getMessage());
        } catch (IOException e) {
            LoggingHelper.log(listener, e.getMessage());
        }
    }
}
