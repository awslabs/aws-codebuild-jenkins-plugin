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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors. Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.*;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CodeBuilderPerformTest extends CodeBuilderTest {

    @Test
    public void testGetCBClientExcepts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "failed to instantiate cb client.";
        doThrow(new InvalidInputException(error)).when(mockFactory).getCodeBuildClient();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
        assert(log.toString().contains(error));
    }

    @Test
    public void testStartBuildExcepts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "submit build exception.";
        doThrow(new InvalidInputException(error)).when(mockClient).startBuild(any(StartBuildRequest.class));
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
        String s = log.toString();
        assert(log.toString().contains(error));
    }

    @Test
    public void testGetBuildExcepts() throws Exception {
        setUpBuildEnvironment();
        String error = "cannot get build";
        doThrow(new InvalidInputException(error)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.perform(build, ws, launcher, listener);
        assert(log.toString().contains(error));

    }

    @Test
    public void testActionConfig() throws Exception {
        setUpBuildEnvironment();
        Date startTime = new Date(0);
        String arn = "arn123:456";
        String logURL = "url1";

        List<String> logs = Arrays.asList("logs");
        when(mockMonitor.getLatestLogs()).thenReturn(logs);
        when(mockBuild.getPhases()).thenReturn(new ArrayList<BuildPhase>());
        when(mockBuild.getStartTime()).thenReturn(startTime);

        LogsLocation mockLogsLocation = new LogsLocation().withDeepLink(logURL);
        when(mockMonitor.getLogsLocation()).thenReturn(mockLogsLocation);
        when(mockBuild.getArn()).thenReturn(arn);

        CodeBuildAction a = mock(CodeBuildAction.class);

        ArgumentCaptor<List<String>> savedLogs = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<BuildPhase>> savedPhases = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> savedBuildArn = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> savedStartTime = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> savedLogURL = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> savedArtifactURL = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> savedBucketName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> savedJStatus = ArgumentCaptor.forClass(Boolean.class);


        CodeBuilder test = createDefaultCodeBuilder();
        fixCodeBuilderFactories(test, mockFactory, mockDataManager, mockProjectFactory);
        test.setAction(a);
        test.setLogMonitor(mockMonitor);
        test.perform(build, ws, launcher, listener);

        verify(a, times(2)).setLogs(savedLogs.capture());
        verify(a, times(2)).setPhases(savedPhases.capture());
        verify(a).setBuildARN(savedBuildArn.capture());
        verify(a).setStartTime(savedStartTime.capture());
        verify(a, times(2)).setLogURL(savedLogURL.capture());
        verify(a).setS3ArtifactURL(savedArtifactURL.capture());
        verify(a).setS3BucketName(savedBucketName.capture());
        verify(a).setJenkinsBuildSucceeds(savedJStatus.capture());

        assert(savedLogs.getValue().size() == 1);
        assert(savedLogs.getValue().get(0).equals(logs.get(0)));
        assert(savedPhases.getValue().equals(new ArrayList<BuildPhase>()));
        assert(savedStartTime.getValue().equals(startTime.toString()));
        assert(savedBuildArn.getValue().equals(arn));
        assert(savedLogURL.getValue().equals(logURL));
        assert(savedArtifactURL.getValue().equals("https://console.aws.amazon.com/s3/home?region=us-east-1#&bucket=artifactBucket"));
        assert(savedBucketName.getValue().equals("artifactBucket"));
        assert(savedJStatus.getValue().equals(true));
    }
}
