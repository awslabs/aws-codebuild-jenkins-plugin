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
import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.util.StringUtils;
import enums.SourceControlType;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

public class CodeBuilder extends Builder implements SimpleBuildStep {

    @Getter private final String sourceControlType;
    @Getter private final String proxyHost;
    @Getter private final String proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final String awsSecretKey;
    @Getter private final String region;
    @Getter private final CodeBuildResult codeBuildResult;
    @Getter private String projectName;
    @Getter private String sourceVersion;
    @Getter private String envVariables;
    @Getter private String buildSpecFile;

    @Setter private String awsClientInitFailureMessage;
    @Setter private AWSClientFactory awsClientFactory;

    @Getter@Setter String artifactLocation;
    @Getter@Setter String artifactType;
    @Getter@Setter String projectSourceLocation;
    @Getter@Setter String projectSourceType;

    //These messages are used in the Jenkins console log.
    public static final String configuredImproperlyError = "CodeBuild configured improperly in project settings \n";
    public static final String generalConfigInvalidError = "Valid credentials and project name are required parameters";
    public static final String s3BucketBaseURL = "https://console.aws.amazon.com/s3/buckets/";
    public static final String envVariableSyntaxError = "CodeBuild environment variable keys and values cannot be empty and the string must be of the form [{key, value}, {key2, value2}]";
    public static final String envVariableNameSpaceError = "CodeBuild environment variable keys cannot start with CODEBUILD_";
    public static final String invalidProjectError = "Please select a project with S3 source type.\n";
    public static final String notVersionsedS3BucketError = "A versioned S3 bucket is required.\n";
    public static final String defaultCredentialsUsedWarning = "AWS access and secret keys were not provided. Using credentials provided by DefaultAWSCredentialsProviderChain.";
    public static final String buildFailedError = "Build failed";


    @DataBoundConstructor
    public CodeBuilder(String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey,
                       String region, String projectName, String sourceVersion, String sourceControlType,
                       String envVariables, String buildSpecFile) {

        this.sourceControlType = sourceControlType;
        this.proxyHost = Validation.sanitize(proxyHost);
        this.proxyPort = Validation.sanitize(proxyPort);
        this.awsAccessKey = Validation.sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.region = region;
        this.projectName = projectName;
        this.sourceVersion = Validation.sanitize(sourceVersion);
        this.envVariables = Validation.sanitize(envVariables);
        this.buildSpecFile = Validation.sanitize(buildSpecFile);
        this.awsClientInitFailureMessage = "";
        this.codeBuildResult = new CodeBuildResult();
        try {
            awsClientFactory = new AWSClientFactory(this.proxyHost, this.proxyPort, this.awsAccessKey, this.awsSecretKey, region);
        } catch(Exception e) {
            awsClientInitFailureMessage = e.getMessage(); //catch the message here and throw it when the actual build runs.
        }
    }

    /*
     * This is the Jenkins method that executes the actual build.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if(!awsClientInitFailureMessage.equals("")) {
            LoggingHelper.log(listener, configuredImproperlyError, awsClientInitFailureMessage);
            String errorMessage = configuredImproperlyError + "\n" + awsClientInitFailureMessage;
            this.codeBuildResult.setFailure(errorMessage);
            return;
        }
        if(!Validation.checkCodeBuilderConfig(this)) {
            LoggingHelper.log(listener, configuredImproperlyError, generalConfigInvalidError);
            String errorMessage = configuredImproperlyError + "\n" + generalConfigInvalidError;
            this.codeBuildResult.setFailure(errorMessage);
            return;
        }
        Collection<EnvironmentVariable> codeBuildEnvVars = null;
        try {
            codeBuildEnvVars = mapEnvVariables(envVariables);
        } catch(InvalidInputException e) {
            LoggingHelper.log(listener, configuredImproperlyError, e.getMessage());
            String errorMessage = configuredImproperlyError + "\n" + e.getMessage();
            this.codeBuildResult.setFailure(errorMessage);
            return;
        }
        if(Validation.envVariablesHaveRestrictedPrefix(codeBuildEnvVars)) {
            LoggingHelper.log(listener, configuredImproperlyError, envVariableNameSpaceError);
            String errorMessage = configuredImproperlyError + "\n" + envVariableNameSpaceError;
            this.codeBuildResult.setFailure(errorMessage);
            return;
        }

        if (awsClientFactory.isDefaultCredentialsUsed()) {
            LoggingHelper.log(listener, defaultCredentialsUsedWarning);
        }
        final AWSCodeBuildClient cbClient;
        try {
            cbClient = awsClientFactory.getCodeBuildClient();
        } catch (Exception e) {
            LoggingHelper.log(listener, e.getMessage());
            this.codeBuildResult.setFailure(e.getMessage());
            return;
        }

        try {
            retrieveArtifactAndSourceInfo(cbClient);
        } catch (Exception e) {
            LoggingHelper.log(listener, e.getMessage());
            this.codeBuildResult.setFailure(e.getMessage());

            return;
        }

        StartBuildRequest startBuildRequest = new StartBuildRequest().withProjectName(this.projectName).
                withEnvironmentVariablesOverride(codeBuildEnvVars).withBuildspecOverride(this.buildSpecFile);

        if(SourceControlType.JenkinsSource.toString().equals(sourceControlType)) {
            if(! Validation.checkSourceTypeS3(this.projectSourceType)) {
                LoggingHelper.log(listener, invalidProjectError, "");
                this.codeBuildResult.setFailure(invalidProjectError);
                return;
            }

            String sourceS3Bucket = Utils.getS3BucketFromObjectArn(this.projectSourceLocation);
            String sourceS3Key = Utils.getS3KeyFromObjectArn(this.projectSourceLocation);
            if(! Validation.checkBucketIsVersioned(sourceS3Bucket, awsClientFactory)) {
                LoggingHelper.log(listener, notVersionsedS3BucketError, "");
                this.codeBuildResult.setFailure(notVersionsedS3BucketError);
                return;
            }

            S3DataManager s3DataManager = new S3DataManager(awsClientFactory.getS3Client(), sourceS3Bucket, sourceS3Key);
            String uploadedSourceVersion = "";


            try {
                LoggingHelper.log(listener, "Uploading source to S3.");
                UploadToS3Output uploadToS3Output = s3DataManager.uploadSourceToS3(listener, ws);
                // Override source version to object version id returned by S3
                LoggingHelper.log(listener, "Source upload finished.");
                if(uploadToS3Output.getObjectVersionId() != null) {
                    uploadedSourceVersion = uploadToS3Output.getObjectVersionId();
                } else {
                    LoggingHelper.log(listener, notVersionsedS3BucketError, "");
                    this.codeBuildResult.setFailure(notVersionsedS3BucketError);
                    return;
                }
                LoggingHelper.log(listener, "S3 object version id for uploaded source is " + uploadedSourceVersion);
            } catch (Exception e) {
                LoggingHelper.log(listener, e.getMessage());
                this.codeBuildResult.setFailure(e.getMessage());
                return;
            }

            startBuildRequest.setSourceVersion(uploadedSourceVersion);
            logStartBuildMessage(listener, projectName, uploadedSourceVersion, buildSpecFile);

        } else {
            startBuildRequest.setSourceVersion(this.sourceVersion);
            logStartBuildMessage(listener, projectName, sourceVersion, buildSpecFile);
        }

        final StartBuildResult sbResult;
        try {
            sbResult = cbClient.startBuild(startBuildRequest);
        } catch (Exception e) {
            LoggingHelper.log(listener, e.getMessage());
            this.codeBuildResult.setFailure(e.getMessage());
            return;
        }

        Build currentBuild;
        String buildId = sbResult.getBuild().getId();
        boolean haveInitializedAction = false;
        CodeBuildAction action = null;
        CloudWatchMonitor logMonitor = null;

        //poll buildResult for build status until it's complete.
        do {
            try {
                List<Build> buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();

                if(buildsForId.size() != 1) {
                    throw new Exception("Multiple builds mapped to this build id.");
                }

                currentBuild = buildsForId.get(0);
                if(!haveInitializedAction) {
                    logMonitor = new CloudWatchMonitor(awsClientFactory.getCloudWatchLogsClient());
                    action = new CodeBuildAction(build);
                    updateDashboard(currentBuild, action, logMonitor, listener);

                    //only need to set these once, the others will need to be updated below as the build progresses.
                    String buildARN = currentBuild.getArn();
                    action.setBuildId(buildId);
                    action.setBuildARN(buildARN);
                    action.setStartTime(currentBuild.getStartTime().toString());
                    action.setS3ArtifactURL(generateS3ArtifactURL(this.s3BucketBaseURL, artifactLocation, artifactType));
                    action.setCodeBuildDashboardURL(generateDashboardURL(buildId));
                    action.setS3BucketName(artifactLocation);

                    build.addAction(action);
                    haveInitializedAction = true;
                }
                Thread.sleep(5000L);
                logMonitor.pollForLogs();
                updateDashboard(currentBuild, action, logMonitor, listener);

            } catch(Exception e) {
                LoggingHelper.log(listener, e.getMessage());
                if(action != null) {
                    action.setJenkinsBuildSucceeds(false);
                }
                this.codeBuildResult.setFailure(e.getMessage());
                return;
            }
        } while(currentBuild.getBuildStatus().equals(StatusType.IN_PROGRESS.toString()));

        this.codeBuildResult.setBuildInformation(currentBuild.getId(), currentBuild.getArn());
        BuildArtifacts artifacts = currentBuild.getArtifacts();
        this.codeBuildResult.setArtifactsLocation(artifacts != null ? artifacts.getLocation() : null);

        if(currentBuild.getBuildStatus().equals(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH))) {
            action.setJenkinsBuildSucceeds(true);
            this.codeBuildResult.setSuccess();
        } else {
            action.setJenkinsBuildSucceeds(false);
            String errorMessage = buildFailedError + " for " + this.projectName + " and source version " + this.sourceVersion;
            LoggingHelper.log(listener, errorMessage);
            this.codeBuildResult.setFailure(errorMessage);
        }
        return;
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
    private void updateDashboard(Build b, CodeBuildAction action, CloudWatchMonitor logMonitor, TaskListener listener) {
        if(action != null) {
            action.setCurrentStatus(b.getBuildStatus());
            action.setLogs(logMonitor.getLatestLogs());
            action.setPhases(b.getPhases());
            logMonitor.setLogsLocation(b.getLogs());
            if (logMonitor.getLogsLocation() != null) {
                if(action.getLogURL() == null){
                    String logUrl = logMonitor.getLogsLocation().getDeepLink();
                    action.setLogURL(logUrl);
                    LoggingHelper.log(listener, "Logs url: " + logUrl);
                }
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
                .append(URLEncoder.encode(artifactLocation, "UTF-8")).toString();
        }
    }

    private String generateDashboardURL(String buildId) {
        return new StringBuilder()
            .append("https://")
            .append(this.region)
            .append(".console.aws.amazon.com/codebuild/home?region=")
            .append(this.region)
            .append("#builds/")
            .append(buildId)
            .append("/view/new").toString();
    }

    private void logStartBuildMessage(TaskListener listener, String projectName, String sourceVersion, String buildSpecFile) {
        StringBuilder message = new StringBuilder().append("Starting build with project name " + projectName);
        if(!sourceVersion.isEmpty()) {
            message.append(" and source version " + sourceVersion);
        }
        if(!buildSpecFile.isEmpty()) {
            message.append(" and build spec " + buildSpecFile);
        }
        LoggingHelper.log(listener, message.toString());
    }

    // Given a String representing environment variables, returns a list of com.amazonaws.services.codebuild.model.EnvironmentVariable
    // objects with the same data. The input string must be in the form [{Key, value}, {k2, v2}] or else null is returned
    public static Collection<EnvironmentVariable> mapEnvVariables(String envVars) throws InvalidInputException {
        Collection<EnvironmentVariable> result = new HashSet<EnvironmentVariable>();
        if(envVars == null || envVars.isEmpty()) {
            return result;
        }

        envVars = envVars.replaceAll("\\s+", "");
        if(envVars.length() < 4 || envVars.charAt(0) != '[' || envVars.charAt(envVars.length()-1) != ']' ||
           envVars.charAt(1) != '{' || envVars.charAt(envVars.length()-2) != '}') {
            throw new InvalidInputException(envVariableSyntaxError);
        } else {
            envVars = envVars.substring(2, envVars.length()-2);
        }

        int numCommas = envVars.replaceAll("[^,]", "").length();
        if(numCommas == 0) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        //single environment variable case vs multiple
        if(numCommas == 1) {
            result.add(deserializeCodeBuildEnvVar(envVars));
        } else {
            String[] evs = envVars.split("\\},\\{");
            for(int i = 0; i < evs.length; i++) {
                result.add(deserializeCodeBuildEnvVar(evs[i]));
            }
        }
        return result;
    }

    // Given a string of the form "key,value", returns a CodeBuild Environment Variable with that data.
    // Throws an
    private static EnvironmentVariable deserializeCodeBuildEnvVar(String ev) throws InvalidInputException {
        if(ev.replaceAll("[^,]", "").length() != 1) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        String[] keyAndValue = ev.split(",");
        if(keyAndValue.length != 2 || keyAndValue[0].isEmpty() || keyAndValue[1].isEmpty()) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        return new EnvironmentVariable().withName(keyAndValue[0]).withValue(keyAndValue[1]);
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

