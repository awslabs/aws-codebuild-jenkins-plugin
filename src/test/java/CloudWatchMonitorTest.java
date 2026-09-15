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

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.codebuild.model.InvalidInputException;
import software.amazon.awssdk.services.codebuild.model.LogsLocation;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class CloudWatchMonitorTest {

    private CloudWatchLogsClient mockClient = mock(CloudWatchLogsClient.class);
    private final String mockGroup = "mock-group";
    private final String mockStream = "mock-stream";
    TaskListener listener = mock(TaskListener.class);

    private void assertLogsContainErrorMessage(CloudWatchMonitor c) {
        assert(c.getLatestLogs().get(0).equals(CloudWatchMonitor.failedConfigurationLogsMessage));
    }

    private void assertLogsContainExceptionMessage(CloudWatchMonitor c, Exception e) {
        assert(c.getLatestLogs().get(0).equals(e.getMessage()));
    }

    @Before
    public void setUp() {
        when(listener.getLogger()).thenReturn(new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    public void testInvalidConfig() throws Exception {
        CloudWatchMonitor c = new CloudWatchMonitor(null, false);
        assertLogsContainErrorMessage(c);
    }

    @Test
    public void testPollExcepts() throws Exception {
        CloudWatchMonitor c = getMockCloudWatchMonitor();
        InvalidInputException e = InvalidInputException.builder().message("no logs").build();
        when(mockClient.getLogEvents(any(GetLogEventsRequest.class))).thenThrow(e);
        c.pollForLogs(listener);
        assertLogsContainExceptionMessage(c, e);
    }

    @Test
    public void testFormatLogs() throws Exception {
        CloudWatchMonitor c = getMockCloudWatchMonitor();
        List<OutputLogEvent> logs = new ArrayList<OutputLogEvent>();
        logs.add(OutputLogEvent.builder().message("[Container] entry 1").build());
        logs.add(OutputLogEvent.builder().message("[Container] entry2").timestamp(1L).build());
        GetLogEventsResponse result = GetLogEventsResponse.builder().events(logs).build();
        when(mockClient.getLogEvents(any(GetLogEventsRequest.class))).thenReturn(result);
        c.pollForLogs(listener);
        assert(c.getLatestLogs().size() == 2);
        assert(c.getLatestLogs().get(0).equals("entry 1"));
        assert(c.getLatestLogs().get(1).equals("entry2"));
        assert(c.getLastPollTime() == 2L);
    }

    @Test
    public void testFormatLogsTwoCalls() throws Exception {
        CloudWatchMonitor c = getMockCloudWatchMonitor();

        List<OutputLogEvent> logsFirst = new ArrayList();
        logsFirst.add(OutputLogEvent.builder().message("[Container] entry 1").build());
        logsFirst.add(OutputLogEvent.builder().message("[Container] entry2").timestamp(1L).build());
        List<OutputLogEvent> logsSecond = new ArrayList();
        logsSecond.add(OutputLogEvent.builder().message("[Container] entry 3").timestamp(3L).build());

        GetLogEventsResponse resultFirst = GetLogEventsResponse.builder().events(logsFirst).build();
        GetLogEventsResponse resultSecond = GetLogEventsResponse.builder().events(logsSecond).nextForwardToken(null).build();

        GetLogEventsRequest requestFirst = GetLogEventsRequest.builder().startTime(0L).startFromHead(true).logGroupName(mockGroup).logStreamName(mockStream).build();
        GetLogEventsRequest requestSecond = GetLogEventsRequest.builder().startTime(2L).startFromHead(true).logGroupName(mockGroup).logStreamName(mockStream).build();

        when(mockClient.getLogEvents(requestFirst)).thenReturn(resultFirst);
        when(mockClient.getLogEvents(requestSecond)).thenReturn(resultSecond);

        c.pollForLogs(listener);
        assert(c.getLatestLogs().size() == 2);
        assert(c.getLatestLogs().get(0).equals("entry 1"));
        assert(c.getLatestLogs().get(1).equals("entry2"));
        assert(c.getLastPollTime() == 2L);

        c.pollForLogs(listener);
        assert(c.getLatestLogs().size() == 1);
        assert(c.getLatestLogs().get(0).equals("entry 3"));
        assert(c.getLastPollTime() == 4L);
    }

    @Test
    public void testCwlStreamingDisabled() throws Exception {
        CloudWatchMonitor c = new CloudWatchMonitor(mockClient, true);
        assertEquals(c.getLatestLogs().size(), 1);
        assertEquals(c.getLatestLogs().get(0), CloudWatchMonitor.streamingDisabledMessage);
        c.pollForLogs(listener);
        assertEquals(c.getLatestLogs().size(), 1);
        assertEquals(c.getLatestLogs().get(0), CloudWatchMonitor.streamingDisabledMessage);
    }

    private CloudWatchMonitor getMockCloudWatchMonitor() {
        CloudWatchMonitor c = new CloudWatchMonitor(mockClient, false);
        c.setLogsLocation(LogsLocation.builder().groupName(mockGroup).streamName(mockStream).build());
        return c;
    }

    @Test
    public void testEarlyReturnConfigInitializesLastPollTime() {
        CloudWatchMonitor c = new CloudWatchMonitor(null, false);
        assertNotNull("lastPollTime must be initialized even on the early-return path", c.getLastPollTime());
        c.pollForLogs(listener);
    }

    @Test
    public void testPollExceptionWithNullMessageYieldsNonNullLogLine() {
        CloudWatchMonitor c = getMockCloudWatchMonitor();
        when(mockClient.getLogEvents(any(GetLogEventsRequest.class))).thenThrow(new RuntimeException((String) null));
        c.pollForLogs(listener);
        assertNotNull("a null exception message must be coalesced to a non-null log line",
                c.getLatestLogs().get(0));
    }
}
