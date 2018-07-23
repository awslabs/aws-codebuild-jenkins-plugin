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

import com.amazonaws.services.codebuild.model.LogsLocation;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.GetLogEventsRequest;
import com.amazonaws.services.logs.model.GetLogEventsResult;
import com.amazonaws.services.logs.model.OutputLogEvent;
import hudson.model.TaskListener;
import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.Marshaller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloudWatchMonitor {

    @Setter private AWSLogsClient logsClient;
    @Setter @Getter  private LogsLocation logsLocation;
    @Getter private List<String> latestLogs;
    @Getter private Long lastPollTime;

    private static final int htmlMaxLineLength = 2000;
    public static final String noLogsMessage = "No CloudWatch logs found for this build.";
    public static final String failedConfigurationLogsMessage = "CloudWatch configuration for this build is incorrect.";

    public CloudWatchMonitor(AWSLogsClient client) {
        this.logsClient = client;
        if(!Validation.checkCloudWatchMonitorConfig(logsClient)) {
            latestLogs = Arrays.asList(failedConfigurationLogsMessage);
            return;
        }
        lastPollTime = 0L;
    }

    // Checks if the CloudWatch logs exist. If they do, retrieves/stores them in this.latestLogs.
    // If the logs don't exist yet, sets this.latestLogs to an error message.
    public void pollForLogs(TaskListener listener) {
        if(this.logsLocation != null) {
            this.latestLogs = new ArrayList();
            GetLogEventsRequest logRequest = new GetLogEventsRequest()
                .withStartTime(lastPollTime)
                .withStartFromHead(true)
                .withLogGroupName(logsLocation.getGroupName())
                .withLogStreamName(logsLocation.getStreamName());
            try {
                GetLogEventsResult logsResult = logsClient.getLogEvents(logRequest);
                getAndFormatLogs(logsResult.getEvents(), listener);
            } catch (Exception e) {
                latestLogs = Arrays.asList(e.getMessage());
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
                String entry = logs.get(i).getMessage();
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
            this.lastPollTime = logs.get(logs.size()-1).getTimestamp() + 1;
        }
    }

}
