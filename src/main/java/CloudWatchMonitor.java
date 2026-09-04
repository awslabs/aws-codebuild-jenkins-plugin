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
 *  Portions copyright Copyright (C) 2015 The Project Lombok Authors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.codebuild.model.LogsLocation;
import hudson.model.TaskListener;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloudWatchMonitor {

    @Setter private CloudWatchLogsClient logsClient;
    @Setter @Getter private LogsLocation logsLocation;
    @Getter private List<String> latestLogs;
    // Fix #9(a): initialized here so it is never null, even when the constructor takes an
    // early-return path (e.g. failed CloudWatch configuration) before reaching the assignment below.
    @Getter private Long lastPollTime = 0L;
    private boolean cwlStreamingDisabled;

    private static final int htmlMaxLineLength = 2000;
    public static final String noLogsMessage = "No CloudWatch logs found for this build.";
    public static final String streamingDisabledMessage = "CloudWatch logs streaming is disabled for this build.";
    public static final String failedConfigurationLogsMessage = "CloudWatch configuration for this build is incorrect.";

    public CloudWatchMonitor(CloudWatchLogsClient client, boolean cwlStreamingDisabled) {
        this.logsClient = client;
        this.cwlStreamingDisabled = cwlStreamingDisabled;
        if(!CodeBuilderValidation.checkCloudWatchMonitorConfig(logsClient)) {
            latestLogs = Arrays.asList(failedConfigurationLogsMessage);
            return;
        }
        if(cwlStreamingDisabled) {
            latestLogs = Arrays.asList(streamingDisabledMessage);
        }
        lastPollTime = 0L;
    }

    // Checks if the CloudWatch logs exist. If they do, retrieves/stores them in this.latestLogs.
    // If the logs don't exist yet, sets this.latestLogs to an error message.
    // Does nothing if CloudWatch logs streaming is disabled
    public void pollForLogs(TaskListener listener) {
        if(cwlStreamingDisabled) {
            return;
        } else if(this.logsLocation != null && this.logsLocation.groupName() != null && this.logsLocation.streamName() != null) {
            this.latestLogs = new ArrayList<>();
            GetLogEventsRequest logRequest = GetLogEventsRequest.builder()
                .startTime(lastPollTime)
                .startFromHead(true)
                .logGroupName(logsLocation.groupName())
                .logStreamName(logsLocation.streamName())
                .build();
            try {
                GetLogEventsResponse logsResult = logsClient.getLogEvents(logRequest);
                getAndFormatLogs(logsResult.events(), listener);
            } catch (Exception e) {
                // Fix #9(b): some exceptions carry a null message; fall back to the class name so
                // the stored log line is never null.
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                latestLogs = Arrays.asList(message);
                return;
            }
        } else {
            latestLogs = Arrays.asList(noLogsMessage);
            return;
        }
    }

    private void getAndFormatLogs(List<OutputLogEvent> logs, TaskListener listener) {
        if(logs.size() != 0) {
            for (int i = 0; i < logs.size(); i++) {
                String entry = logs.get(i).message();
                //trim the [Container] string from the log message.
                if(entry.startsWith("[Container]")) {
                    entry = entry.substring(entry.indexOf("]") + 2);
                }
                if (entry.length() > htmlMaxLineLength) {
                    entry = Utils.formatStringWithEllipsis(entry, htmlMaxLineLength);
                }
                LoggingHelper.log(listener, entry.replace("\n", ""));
                latestLogs.add(entry);
            }
            this.lastPollTime = logs.get(logs.size()-1).timestamp() + 1;
        }
    }

}
