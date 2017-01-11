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
import com.amazonaws.services.logs.model.OutputLogEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CloudWatchMonitor {

    @Setter private AWSLogsClient logsClient;
    @Setter@Getter  private LogsLocation logsLocation;
    @Getter private List<String> latestLogs;

    private static final int LOG_LIMIT = 15;
    private static final int htmlMaxLineLength = 200;
    public static final String noLogsMessage = "Fetching CloudWatch logs for this build.";
    public static final String failedConfigurationLogsMessage = "CloudWatch configuration for this build is incorrect.";

    public CloudWatchMonitor(AWSLogsClient client) {
        this.logsClient = client;
        if(!Validation.checkCloudWatchMonitorConfig(logsClient)) {
            latestLogs = Arrays.asList(failedConfigurationLogsMessage);
            return;
        }
    }

    // Checks if the CloudWatch logs exist. If they do, retrieves/stores them in this.containersAndLogs.
    // If the logs don't exist yet, sets this.logs to an error message.
    public void pollForLogs() {
        if(this.logsLocation != null) {
            GetLogEventsRequest logRequest = new GetLogEventsRequest()
                .withStartFromHead(false)
                .withLimit(LOG_LIMIT)
                .withLogGroupName(logsLocation.getGroupName())
                .withLogStreamName(logsLocation.getStreamName());

            try {
                formatLogs(logsClient.getLogEvents(logRequest).getEvents());
            } catch (Exception e) {
                latestLogs = Arrays.asList(e.getMessage());
                return;
            }
        } else {
            latestLogs = Arrays.asList(noLogsMessage);
            return;
        }

    }

    private void formatLogs(List<OutputLogEvent> logs) {
        if(logs.size() != 0) {
            String entry = logs.get(0).getMessage();
            if(entry.contains("[") && entry.contains("]")) {
                this.latestLogs = new ArrayList<String>();
                for (int i = 0; i < logs.size(); i++) {
                    entry = logs.get(i).getMessage();
                    //trim the [Container] string from the log message.
                    entry = entry.substring(entry.indexOf("]") + 2);
                    if (entry.length() > htmlMaxLineLength) {
                        entry = entry.substring(0, htmlMaxLineLength - "...".length()) + "...";
                    }
                    latestLogs.add(entry);
                }
            }
        }
    }

}
