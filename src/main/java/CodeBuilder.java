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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (c) 2009-, Kohsuke Kawaguchi and other contributors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.*;
import enums.SourceControlType;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

public class CodeBuilder extends Builder {

    @Getter private final String sourceControlType;
    @Getter private final String proxyHost;
    @Getter private final String proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final String awsSecretKey;
    @Getter private final String region;
    @Getter private String projectName;
    @Getter private String sourceVersion;

    @Setter private S3DataManager s3DataManager;
    @Setter private String awsClientInitFailureMessage;
    @Setter private AWSClientFactory awsClientFactory;
    @Setter private CloudWatchMonitor logMonitor;
    @Setter private CodeBuildAction action;

    @Getter@Setter String artifactLocation;
    @Getter@Setter String artifactType;
    @Getter@Setter String projectSourceLocation;
    @Getter@Setter String projectSourceType;

    //These messages are used in the Jenkins console log.
    public static final String configuredImproperlyError = "CodeBuild configured improperly in project settings \n";
    public static final String generalConfigInvalidError = "Valid credentials and project name are required parameters";
    public static final String s3DashboardURL = "https://console.aws.amazon.com/s3/home?";
    public static final String invalidProjectError = "Please select a project with S3 source type.\n";
    public static final String notVersionsedS3BucketError = "A versioned S3 bucket is required.\n";

    @DataBoundConstructor
    public CodeBuilder(String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey,
                       String region, String projectName, String sourceVersion, String sourceControlType) {

        this.sourceControlType = sourceControlType;
        this.proxyHost = Validation.sanitize(proxyHost);
        this.proxyPort = Validation.sanitize(proxyPort);
        this.awsAccessKey = Validation.sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.region = region;
        this.projectName = projectName;
        this.sourceVersion = Validation.sanitize(sourceVersion);
        this.awsClientInitFailureMessage = "";
        try {
            awsClientFactory = new AWSClientFactory(this.proxyHost, this.proxyPort, this.awsAccessKey, this.awsSecretKey, region);
        } catch(Exception e) {
            awsClientInitFailureMessage = e.getMessage(); //catch the message here and throw it when the actual build runs.
        }
    }


    /*
     * This is the Jenkins method that executes the actual build.
     * @return true if the build succeeds and false otherwise.
     */
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if(!awsClientInitFailureMessage.equals("")) {
            LoggingHelper.log(listener, configuredImproperlyError, awsClientInitFailureMessage);
            return false;
        }
        if(!Validation.checkCodeBuilderConfig(this)) {
            LoggingHelper.log(listener, configuredImproperlyError, generalConfigInvalidError);
            return false;
        }

        final AWSCodeBuildClient cbClient;
        try {
            cbClient = awsClientFactory.getCodeBuildClient();
        } catch (Exception e) {
            LoggingHelper.log(listener, e.getMessage());
            return false;
        }

        try {
            retrieveArtifactAndSourceInfo(cbClient);
        } catch (Exception e) {
            logErrorAndNullifyBuildComponents(listener, e.getMessage(), "");
            return false;
        }

        if(SourceControlType.JenkinsSource.toString().equals(sourceControlType)) {
            if(! Validation.checkSourceTypeS3(this.projectSourceType)) {
                LoggingHelper.log(listener, invalidProjectError, "");
                return false;
            }

            String sourceS3Bucket = Utils.getS3BucketFromObjectArn(this.projectSourceLocation);
            String sourceS3Key = Utils.getS3KeyFromObjectArn(this.projectSourceLocation);
            LoggingHelper.log(listener, "Source S3 bucket is " + sourceS3Bucket);
            if(! Validation.checkBucketIsVersioned(sourceS3Bucket, awsClientFactory)) {
                LoggingHelper.log(listener, notVersionsedS3BucketError, "");
                return false;
            }

            if(s3DataManager == null) {
                s3DataManager = new S3DataManager(build.getProject().getFullName(),
                        build.getWorkspace(),
                        build.getFullDisplayName(),
                        awsClientFactory.getS3Client(),
                        sourceS3Bucket,
                        sourceS3Key
                        );
            }
            try {
                LoggingHelper.log(listener, "Uploading source to S3.");
                UploadToS3Output uploadToS3Output = s3DataManager.uploadSourceToS3(build, launcher, listener);
                // Override source version to object version id returned by S3
                LoggingHelper.log(listener, "Source upload finished.");
                if(uploadToS3Output.getObjectVersionId() != null) {
                    this.sourceVersion = uploadToS3Output.getObjectVersionId();
                } else {
                    LoggingHelper.log(listener, notVersionsedS3BucketError, "");
                    return false;
                }
                LoggingHelper.log(listener, "S3 object version id for uploaded source is " + this.sourceVersion);
            } catch (Exception e) {
                logErrorAndNullifyBuildComponents(listener, e.getMessage(), "");
                return false;
            }
        }

        StartBuildRequest startBuildRequest = new StartBuildRequest()
                .withProjectName(this.projectName).withSourceVersion(sourceVersion);
        LoggingHelper.log(listener, "Starting build with projectName " + this.projectName + " and source version " + this.sourceVersion);
        final StartBuildResult sbResult;
        try {
            sbResult = cbClient.startBuild(startBuildRequest);
        } catch (Exception e) {
            logErrorAndNullifyBuildComponents(listener, e.getMessage(), "");
            return false;
        }

        Build currentBuild;
        String buildId = sbResult.getBuild().getId();
        boolean haveInitializedAction = false;

        if(action == null) {
            action = new CodeBuildAction(build); //the entity that creates the codebuild dashboard.
        }

        //poll buildResult for build status until it's complete.
        do {
            try {
                List<Build> buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();

                if(buildsForId.size() != 1) {
                    throw new Exception("Multiple builds mapped to this build id.");
                }

                currentBuild = buildsForId.get(0);

                if(!haveInitializedAction) {
                    if(logMonitor == null) {
                        logMonitor = new CloudWatchMonitor(awsClientFactory.getCloudWatchLogsClient());
                    }

                    updateDashboard(currentBuild);

                    //only need to set these once, the others will need to be updated below as the build progresses.
                    String buildARN = currentBuild.getArn();
                    action.setBuildARN(buildARN);
                    action.setStartTime(currentBuild.getStartTime().toString());
                    action.setS3ArtifactURL(generateS3ArtifactURL(this.s3DashboardURL, artifactLocation, artifactType));
                    action.setS3BucketName(artifactLocation);

                    build.addAction(action);
                    haveInitializedAction = true;
                }
                Thread.sleep(5000L);
                logMonitor.pollForLogs();
                updateDashboard(currentBuild);

            } catch(Exception e) {
                logErrorAndNullifyBuildComponents(listener, e.getMessage(), "");
                return failActionAndReturnFalse();
            }
        } while(currentBuild.getBuildStatus().equals(StatusType.IN_PROGRESS.toString()));

        Boolean jenkinsBuildResult;
        if(currentBuild.getBuildStatus().equals(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH))) {
            action.setJenkinsBuildSucceeds(true);
            jenkinsBuildResult = true;
        } else {
            action.setJenkinsBuildSucceeds(false);
            jenkinsBuildResult = false;
        }

        nullifyBuildComponents();
        return jenkinsBuildResult;
    }

    // finds the name of the artifact S3 bucket associated with this project.
    // Sets this.artifactLocation equal to it and updates this.action.
    // @param cbClient: the CodeBuild client used by this build.
    private void retrieveArtifactAndSourceInfo(AWSCodeBuildClient cbClient) throws Exception {
        BatchGetProjectsResult bgpResult = cbClient.batchGetProjects(
                new BatchGetProjectsRequest().withNames(this.projectName));

        if(bgpResult.getProjects().isEmpty()) {
            throw new RuntimeException("Project " + this.projectName + " does not exist.");
        } else {
            this.artifactLocation = bgpResult.getProjects().get(0).getArtifacts().getLocation();
            this.artifactType = bgpResult.getProjects().get(0).getArtifacts().getType();

            this.projectSourceLocation = bgpResult.getProjects().get(0).getSource().getLocation();
            this.projectSourceType = bgpResult.getProjects().get(0).getSource().getType();
        }
    }

    // Performs an update of build data to the codebuild dashboard.
    // @param action: the entity representing the dashboard.
    private void updateDashboard(Build b) {
        if(action != null) {
            action.setCurrentStatus(b.getBuildStatus());
            action.setLogs(logMonitor.getLatestLogs());
            action.setPhases(b.getPhases());
            logMonitor.setLogsLocation(b.getLogs());
            if (logMonitor.getLogsLocation() != null) {
                action.setLogURL(logMonitor.getLogsLocation().getDeepLink());
            }
        }
    }

    // @param baseURL: a link to the S3 dashboard for the user running the build.
    // @param buildARN: the ARN for this build.
    // @return: a URL to the S3 artifacts for this build.
    public String generateS3ArtifactURL(String baseURL, String artifactLocation, String artifactType) throws UnsupportedEncodingException {
        if(artifactLocation == null || artifactLocation.isEmpty() ||
                artifactType == null || !artifactType.equals(ArtifactsType.S3.toString())) {
            return "";
        } else {
            return new StringBuilder()
                .append(baseURL)
                .append("region=" + this.region + "#")
                .append("&bucket=" + URLEncoder.encode(artifactLocation, "UTF-8")).toString();
        }
    }

    private void logErrorAndNullifyBuildComponents(BuildListener listener, String error, String secondaryError) {
        LoggingHelper.log(listener, error, secondaryError);
        nullifyBuildComponents();
    }

    // Notify the CodeBuild dashboard that the build failed, then return false;
    private boolean failActionAndReturnFalse() {
        if(this.action != null) {
            this.action.setJenkinsBuildSucceeds(false);
        }
        return false;
    }

    //sets these fields to null so they are reinstantiated on the next build and not reused.
    private void nullifyBuildComponents() {
        this.logMonitor = null;
        this.action = null;
    }

    //// Jenkins-specific functions ////
    // all for CodeBuilder/config.jelly
    public String sourceControlTypeEquals(String given) {
        return String.valueOf((sourceControlType != null) && (sourceControlType.equals(given)));
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for CodeBuilder. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private String externalId;
        private String proxyHost;
        private String proxyPort;

        public DescriptorImpl() {
            load();

            if(externalId == null) {
                externalId = UUID.randomUUID().toString();
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            if(formData.has("proxyHost")) {
              proxyHost = formData.getString("proxyHost");
            }
            if(formData.has("proxyPort")) {
              proxyPort = formData.getString("proxyPort");
            }

            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return "Run build on AWS CodeBuild";
        }
    }
}

