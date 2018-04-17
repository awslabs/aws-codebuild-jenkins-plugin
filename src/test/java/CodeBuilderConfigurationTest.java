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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import enums.SourceControlType;
import hudson.model.Result;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CodeBuilderConfigurationTest extends CodeBuilderTest {


    @Test
    public void testConfigAllNull() throws IOException, ExecutionException, InterruptedException {
        CodeBuilder test = new CodeBuilder(null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null);

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertTrue(log.toString().contains(CodeBuilder.authorizationError));
    }

    @Test
    public void testConfigAllNullPipeline() throws IOException, ExecutionException, InterruptedException {
        CodeBuilder test = new CodeBuilder(null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null);

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);

        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.authorizationError));
    }

    @Test
    public void testConfigAllBlank() throws IOException, ExecutionException, InterruptedException {
        CodeBuilder test = new CodeBuilder("", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "","","","","",     "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assert(log.toString().contains(CodeBuilder.authorizationError));
    }

    @Test
    public void testConfigAllBlankPipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "","","","","",     "", "", "", "");

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);

        assert(log.toString().contains("Enter a valid AWS region"));
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.authorizationError));
    }

    @Test
    public void testNoProjectName() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "",
                "us-east-1", "", "", "", SourceControlType.ProjectSource.toString(),
                "", "", "", "", "", "",
                "","", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();
        assertTrue(log.toString().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(log.toString().contains(Validation.projectRequiredError));
    }

    @Test
    public void testNoSourceType() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "",
                "us-east-1", "project", "", "", "",
                "", "", "", "", "", "",
                "","", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();
        assertTrue(log.toString().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(log.toString().contains(Validation.sourceControlTypeRequiredError));
    }

    @Test
    public void testInvalidAWSCredentials() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new com.amazonaws.AmazonServiceException("The security token included in " +
                        "the request is invalid"));

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
    }

    @Test
    public void testInvalidAWSCredentialsPipeline() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new com.amazonaws.AmazonServiceException("The security token included in " +
                        "the request is invalid"));

        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);

        test.perform(build, ws, launcher, listener);

        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    @Test
    public void testInvalidGitLocation() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("service only supports https protocol for GIT endpoints"));

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
    }

    @Test
    public void testInvalidGitLocationPipeline() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("service only supports https protocol for GIT endpoints"));

        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);

        test.perform(build, ws, launcher, listener);

        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    @Test
    public void testInvalidEnvARN() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("The provided ARN(" + "123" + ") is invalid."));

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
    }

    @Test
    public void testInvalidEnvARNPipeline() throws IOException, InterruptedException {
        when(mockFactory.getCodeBuildClient()).thenReturn(mockClient);
        when(mockClient.startBuild(any(StartBuildRequest.class)))
                .thenThrow(new InvalidInputException("The provided ARN(" + "123" + ") is invalid."));

        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);

        test.perform(build, ws, launcher, listener);

        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }
}

