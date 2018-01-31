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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (C) 2015 The Project Lombok Authors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.BuildPhase;
import com.amazonaws.services.codebuild.model.StatusType;
import hudson.model.Action;
import hudson.model.Run;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Data
public class CodeBuildAction implements Action {

    private final Run<?, ?> build;

    private String buildId;
    private List<String> logs;
    private String logURL;
    private List<BuildPhase> phases;
    private String phaseErrorMessage;
    private String startTime;
    private String currentPhase;
    private String currentStatus;

    private String environmentARN;
    private String buildARN;
    private String sourceType;
    private String sourceLocation;
    private String sourceVersion;
    private String gitCloneDepth;
    private String s3BucketName;
    private String s3ArtifactURL;
    private String artifactTypeOverride;
    private String codeBuildDashboardURL;
    private Boolean jenkinsBuildSucceeds;

    private static final int MAX_DASHBOARD_NAME_LENGTH = 15;


    @Override
    public String getIconFileName() {
        return "star-gold.png";
    }

    @Override
    public String getDisplayName() {
        return "CodeBuild: " + Utils.formatStringWithEllipsis(getBuildId(), MAX_DASHBOARD_NAME_LENGTH);
    }

    @Override
    public String getUrlName() {
        String id = getBuildId();
        return id.substring(id.indexOf(":")+1, id.length());
    }

    // Sets the state of the latest phase to be in_progress (unless the latest phase is completed, in which
    // case the state is set to succeeded).
    private void formatLatestPhase() {
        if(phases != null && !phases.isEmpty()) {
            BuildPhase latest = phases.get(phases.size() - 1);
            if(latest.getPhaseStatus() == null) {
                if(latest.getPhaseType().equals("COMPLETED")) {
                    latest.setPhaseStatus(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH));
                } else {
                    latest.setPhaseStatus("IN PROGRESS");
                }
            }
            latest.setDurationInSeconds(0L);
        }
    }

    public List<BuildPhase> getPhases() {
        formatLatestPhase();
        return phases;
    }

    public String getJenkinsBuildSucceeds() {
        if(jenkinsBuildSucceeds == null) {
            return "";
        }
        return jenkinsBuildSucceeds.toString();
    }

    //return the finish time of the build.
    public String getFinishTime() {
        if(getCurrentBuildPhase().getPhaseType().equals("COMPLETED")) {
            return getCurrentBuildPhase().getStartTime().toString();
        } else {
            return "-";
        }
    }

    public String getCurrentPhase() {
        return getCurrentBuildPhase().getPhaseType();
    }

    private BuildPhase getCurrentBuildPhase() {
        if(phases == null || phases.isEmpty()) {
            return new BuildPhase().withPhaseType("-");
        }
        return phases.get(phases.size()-1);
    }

    public String getPhaseErrorMessage() {
        BuildPhase errorPhase = getErrorPhase();
        if(errorPhase == null) {
            return "";
        } else {
            if (!errorPhase.getContexts().isEmpty()) {
                return errorPhase.getContexts().get(0).getMessage().replace("'", "").replace("\n", "") + " (status code: " +
                        errorPhase.getContexts().get(0).getStatusCode() + ")";
            } else {
                return "";
            }
        }
    }

    public String getErrorPhaseType() {
        BuildPhase errorPhase = getErrorPhase();
        if(errorPhase == null) {
            return "";
        } else {
            return errorPhase.getPhaseType();
        }
    }

    private BuildPhase getErrorPhase() {
        if(phases != null) {
            for(BuildPhase p: phases) {
                String status = p.getPhaseStatus();
                if(status != null) {
                    if(status.equals(StatusType.FAULT.toString().toUpperCase(Locale.ENGLISH)) ||
                        status.equals("CLIENT_ERROR") ||
                        status.equals(StatusType.FAILED.toString().toUpperCase(Locale.ENGLISH))) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    //Dashboard summary doesn't appear when these getters are missing.
    public String getBuildStatus() {
        if(currentStatus != null && currentStatus.equals(StatusType.IN_PROGRESS.toString())) {
            return "IN PROGRESS"; //instead of in_progress
        }
        return currentStatus;
    }

    public void updateLogs(List<String> newLogs) {
        if(logs != null) {
            if(logs.size() == 1 && logs.get(0).equals(CloudWatchMonitor.noLogsMessage)) { //no logs message already displayed
                if(newLogs.size() > 0 && !newLogs.get(0).equals(CloudWatchMonitor.noLogsMessage)) {
                    logs = new ArrayList();
                } else {
                    return;
                }
            }
            this.logs.addAll(newLogs);
        }
    }
}
