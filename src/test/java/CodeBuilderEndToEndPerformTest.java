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

import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest;
import com.amazonaws.services.codebuild.model.BatchGetBuildsResult;
import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.StatusType;
import hudson.model.Result;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CodeBuilderEndToEndPerformTest extends CodeBuilderTest {

    @Test
    public void testBuildSuccess() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        fixCodeBuilderFactories(test, mockFactory);
        test.perform(build, ws, launcher, listener);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
    }

    @Test
    public void testBuildThenWaitThenSuccess() throws Exception {
        setUpBuildEnvironment();
        Build inProgress = new Build().withBuildStatus(StatusType.IN_PROGRESS).withStartTime(new Date(1));
        Build succeeded = new Build().withBuildStatus(StatusType.SUCCEEDED.toString().toUpperCase()).withStartTime(new Date(2));
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class))).thenReturn(
                new BatchGetBuildsResult().withBuilds(inProgress),
                new BatchGetBuildsResult().withBuilds(inProgress),
                new BatchGetBuildsResult().withBuilds(succeeded));
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        fixCodeBuilderFactories(test, mockFactory);
        test.perform(build, ws, launcher, listener);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.SUCCESS);
    }

    @Test
    public void testBuildFails() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        when(mockBuild.getBuildStatus()).thenReturn(StatusType.FAILED.toString().toUpperCase());
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        fixCodeBuilderFactories(test, mockFactory);
        test.perform(build, ws, launcher, listener);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
    }

    @Test
    public void testBuildThenWaitThenFails() throws Exception {
        setUpBuildEnvironment();
        Build inProgress = new Build().withBuildStatus(StatusType.IN_PROGRESS).withStartTime(new Date(1));
        Build failed = new Build().withBuildStatus(StatusType.FAILED).withStartTime(new Date(2));
        when(mockClient.batchGetBuilds(any(BatchGetBuildsRequest.class))).thenReturn(
                new BatchGetBuildsResult().withBuilds(inProgress),
                new BatchGetBuildsResult().withBuilds(inProgress),
                new BatchGetBuildsResult().withBuilds(failed));
        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory);
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);
        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
    }
}
