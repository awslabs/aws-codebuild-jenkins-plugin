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

import hudson.AbortException;
import hudson.model.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.codebuild.model.BatchGetBuildsRequest;
import software.amazon.awssdk.services.codebuild.model.BatchGetBuildsResponse;
import software.amazon.awssdk.services.codebuild.model.Build;
import software.amazon.awssdk.services.codebuild.model.BuildArtifacts;
import software.amazon.awssdk.services.codebuild.model.BuildPhaseType;
import software.amazon.awssdk.services.codebuild.model.StatusType;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CodeBuilderEndToEndPerformTest extends CodeBuilderTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Before
    public void SetUp() throws Exception {
        setUpBuildEnvironment();
    }

    @Test
    public void testBuildSuccess() throws Exception {
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        CodeBuilder test = createDefaultCodeBuilder();

        test.perform(build, ws, launcher, listener, mockStepContext);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.SUCCESS, result.getStatus());
    }

    @Test
    public void testBuildThenWaitThenSuccess() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).startTime(Instant.ofEpochMilli(1)).build();
        Build succeeded = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.SUCCEEDED.toString().toUpperCase()).startTime(Instant.ofEpochMilli(2)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class))).thenReturn(
                BatchGetBuildsResponse.builder().builds(inProgress).build(),
                BatchGetBuildsResponse.builder().builds(inProgress).build(),
                BatchGetBuildsResponse.builder().builds(succeeded).build());
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.SUCCESS, result.getStatus());
    }

    @Test
    public void testBuildFails() throws Exception {
        CodeBuilder test = createDefaultCodeBuilder();
        when(mockBuild.buildStatusAsString()).thenReturn(StatusType.FAILED.toString().toUpperCase());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    @Test
    public void testBuildThenWaitThenFails() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).startTime(Instant.ofEpochMilli(1)).build();
        Build failed = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.FAILED).startTime(Instant.ofEpochMilli(2)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class))).thenReturn(
                BatchGetBuildsResponse.builder().builds(inProgress).build(),
                BatchGetBuildsResponse.builder().builds(inProgress).build(),
                BatchGetBuildsResponse.builder().builds(failed).build());
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    @Test
    public void testBatchGetBuildsHttpTimeout() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).startTime(Instant.ofEpochMilli(1)).build();
        Build succeeded = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.SUCCEEDED.toString().toUpperCase()).startTime(Instant.ofEpochMilli(2)).build();

        SdkClientException ex = SdkClientException.builder().message("Unable to execute HTTP request: connect timed out").build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .thenThrow(ex)
                .thenReturn(BatchGetBuildsResponse.builder().builds(succeeded).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
    }

    @Test
    public void testBatchGetBuildsMultipleHttpTimeout() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).startTime(Instant.ofEpochMilli(1)).build();
        Build succeeded = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.SUCCEEDED.toString().toUpperCase()).startTime(Instant.ofEpochMilli(2)).build();

        SdkClientException ex = SdkClientException.builder().message("Unable to execute HTTP request: connect timed out").build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .thenThrow(ex)
                .thenThrow(ex)
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .thenReturn(BatchGetBuildsResponse.builder().builds(succeeded).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener, mockStepContext);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
    }

    @Test
    public void testInterruptedBuild() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).currentPhase(BuildPhaseType.BUILD.toString()).startTime(Instant.ofEpochMilli(1)).build();
        Build stopped = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.STOPPED).currentPhase(BuildPhaseType.COMPLETED.toString()).startTime(Instant.ofEpochMilli(2)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .then(mockInterruptedException)
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .thenReturn(BatchGetBuildsResponse.builder().builds(stopped).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.ABORTED);
    }

    @Test
    public void testInterruptedCompletedBuild() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.IN_PROGRESS).currentPhase(BuildPhaseType.BUILD.toString()).startTime(Instant.ofEpochMilli(1)).build();
        Build completed = Build.builder().artifacts(BuildArtifacts.builder().build()).buildStatus(StatusType.SUCCEEDED).currentPhase(BuildPhaseType.COMPLETED.toString()).startTime(Instant.ofEpochMilli(2)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .then(mockInterruptedException)
                .thenReturn(BatchGetBuildsResponse.builder().builds(completed).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.ABORTED);
    }

    @Test
    public void testBuildFailsWithExceptionFailureMode() throws Exception {
        //exceptionFailureMode should be enabled
        CodeBuilder test = new CodeBuilder("", "", "", "", "", null, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
                                           "ENABLED", "DISABLED", "");

        exception.expect(AbortException.class);
        exception.expectMessage(CodeBuilder.configuredImproperlyError + "\n\t> " + CodeBuilderValidation.projectRequiredError);
        test.perform(build, ws, launcher, listener, mockStepContext);

        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
    }

    @Test
    public void testInterruptBeforeFirstPoll() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build())
                .buildStatus(StatusType.IN_PROGRESS).currentPhase(BuildPhaseType.BUILD.toString())
                .startTime(Instant.ofEpochMilli(1)).build();
        Build stopped = Build.builder().artifacts(BuildArtifacts.builder().build())
                .buildStatus(StatusType.STOPPED).currentPhase(BuildPhaseType.COMPLETED.toString())
                .startTime(Instant.ofEpochMilli(2)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .then(mockInterruptedException)
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .thenReturn(BatchGetBuildsResponse.builder().builds(stopped).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(Result.ABORTED, savedResult.getValue());
    }

    @Test
    public void testInterruptWithNullCurrentPhase() throws Exception {
        Build inProgress = Build.builder().artifacts(BuildArtifacts.builder().build())
                .buildStatus(StatusType.IN_PROGRESS).currentPhase(BuildPhaseType.BUILD.toString())
                .startTime(Instant.ofEpochMilli(1)).build();
        Build nullPhase = Build.builder().artifacts(BuildArtifacts.builder().build())
                .buildStatus(StatusType.IN_PROGRESS).startTime(Instant.ofEpochMilli(2)).build();
        Build completed = Build.builder().artifacts(BuildArtifacts.builder().build())
                .buildStatus(StatusType.STOPPED).currentPhase(BuildPhaseType.COMPLETED.toString())
                .startTime(Instant.ofEpochMilli(3)).build();
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class)))
                .thenReturn(BatchGetBuildsResponse.builder().builds(inProgress).build())
                .then(mockInterruptedException)
                .thenReturn(BatchGetBuildsResponse.builder().builds(nullPhase).build())
                .thenReturn(BatchGetBuildsResponse.builder().builds(completed).build());

        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener, mockStepContext);

        verify(build).setResult(savedResult.capture());
        assertEquals(Result.ABORTED, savedResult.getValue());
    }
}
