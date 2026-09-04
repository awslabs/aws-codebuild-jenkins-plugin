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

import software.amazon.awssdk.services.codebuild.model.BuildPhase;
import software.amazon.awssdk.services.codebuild.model.StatusType;
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
    private String cloudWatchLogsURL;
    private String s3LogsURL;
    // v1 -> v2: SDK v2 model types (software.amazon.awssdk.*) are refused by Jenkins' XStream
    // security class filter, so BuildPhase can no longer be persisted to build.xml. Marking the
    // field transient keeps all in-memory dashboard behavior identical; the only effect is that
    // the phase list is not restored across a Jenkins restart.
    private transient List<BuildPhase> phases;
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
    private String reportBuildStatus;
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
    // v1 -> v2: BuildPhase is immutable in SDK v2, so instead of mutating the phase in place we rebuild
    // it via toBuilder() and replace the last element in a fresh list.
    private void formatLatestPhase() {
        if(phases != null && !phases.isEmpty()) {
            BuildPhase latest = phases.get(phases.size() - 1);
            BuildPhase.Builder builder = latest.toBuilder();
            if(latest.phaseStatusAsString() == null) {
                if("COMPLETED".equals(latest.phaseTypeAsString())) {
                    builder.phaseStatus(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH));
                } else {
                    builder.phaseStatus("IN PROGRESS");
                }
            }
            builder.durationInSeconds(0L);
            List<BuildPhase> updated = new ArrayList<>(phases);
            updated.set(updated.size() - 1, builder.build());
            phases = updated;
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
        if(getCurrentBuildPhase().phaseTypeAsString().equals("COMPLETED")) {
            return getCurrentBuildPhase().startTime().toString();
        } else {
            return "-";
        }
    }

    public String getCurrentPhase() {
        return getCurrentBuildPhase().phaseTypeAsString();
    }

    private BuildPhase getCurrentBuildPhase() {
        if(phases == null || phases.isEmpty()) {
            return BuildPhase.builder().phaseType("-").build();
        }
        return phases.get(phases.size()-1);
    }

    public String getPhaseErrorMessage() {
        BuildPhase errorPhase = getErrorPhase();
        if(errorPhase == null) {
            return "";
        } else {
            if (!errorPhase.contexts().isEmpty()) {
                return errorPhase.contexts().get(0).message().replace("'", "").replace("\n", "") + " (status code: " +
                        errorPhase.contexts().get(0).statusCode() + ")";
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
            return errorPhase.phaseTypeAsString();
        }
    }

    private BuildPhase getErrorPhase() {
        if(phases != null) {
            for(BuildPhase p: phases) {
                String status = p.phaseStatusAsString();
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
            if(logs.size() == 1) {
                if(logs.get(0).equals(CloudWatchMonitor.noLogsMessage)) {
                    if (newLogs.size() > 0 && !newLogs.get(0).equals(CloudWatchMonitor.noLogsMessage)) {
                        logs = new ArrayList<>();
                    } else {
                        return;
                    }
                } else if(logs.get(0).equals(CloudWatchMonitor.streamingDisabledMessage)) {
                    return;
                }
            }
            this.logs.addAll(newLogs);
        }
    }
}
