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
import com.amazonaws.services.codebuild.model.LogsLocation;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.OutputLogEvent;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;

public class CloudWatchMonitorTest {

    private AWSLogsClient mockClient = mock(AWSLogsClient.class);
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
        InvalidInputException e = new InvalidInputException("no logs");
        when(mockClient.getLogEvents(any(GetLogEventsRequest.class))).thenThrow(e);
        c.pollForLogs(listener);
        assertLogsContainExceptionMessage(c, e);
    }

    @Test
    public void testFormatLogs() throws Exception {
        CloudWatchMonitor c = getMockCloudWatchMonitor();
        List<OutputLogEvent> logs = new ArrayList<OutputLogEvent>();
        logs.add(new OutputLogEvent().withMessage("[Container] entry 1"));
        logs.add(new OutputLogEvent().withMessage("[Container] entry2").withTimestamp(1L));
        GetLogEventsResult result = new GetLogEventsResult().withEvents(logs);
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
        logsFirst.add(new OutputLogEvent().withMessage("[Container] entry 1"));
        logsFirst.add(new OutputLogEvent().withMessage("[Container] entry2").withTimestamp(1L));
        List<OutputLogEvent> logsSecond = new ArrayList();
        logsSecond.add(new OutputLogEvent().withMessage("[Container] entry 3").withTimestamp(3L));

        GetLogEventsResult resultFirst = new GetLogEventsResult().withEvents(logsFirst);
        GetLogEventsResult resultSecond = new GetLogEventsResult().withEvents(logsSecond).withNextForwardToken(null);

        GetLogEventsRequest requestFirst = new GetLogEventsRequest().withStartTime(0L).withStartFromHead(true).withLogGroupName(mockGroup).withLogStreamName(mockStream);
        GetLogEventsRequest requestSecond = new GetLogEventsRequest().withStartTime(2L).withStartFromHead(true).withLogGroupName(mockGroup).withLogStreamName(mockStream);

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
        c.setLogsLocation(new LogsLocation().withGroupName(mockGroup).withStreamName(mockStream));
        return c;
    }
}
