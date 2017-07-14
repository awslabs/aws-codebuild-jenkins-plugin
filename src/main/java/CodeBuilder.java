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
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import enums.CodeBuildRegions;
import enums.SourceControlType;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
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

    @Getter private final String credentialsType;
    @Getter private final String credentialsId;
    @Getter private final String proxyHost;
    @Getter private final String proxyPort;
    @Getter private final String awsAccessKey;
    @Getter private final String awsSecretKey;
    @Getter private final String region;
    @Getter private String projectName;
    @Getter private final String sourceControlType;
    @Getter private String sourceVersion;

    @Getter private String artifactTypeOverride;
    @Getter private String artifactLocationOverride;
    @Getter private String artifactNameOverride;
    @Getter private String artifactNamespaceOverride;
    @Getter private String artifactPackagingOverride;
    @Getter private String artifactPathOverride;

    @Getter private String envVariables;
    @Getter private String buildSpecFile;
    @Getter private String buildTimeoutOverride;

    @Getter private final CodeBuildResult codeBuildResult;

    @Getter@Setter String artifactLocation;
    @Getter@Setter String artifactType;
    @Getter@Setter String projectSourceLocation;
    @Getter@Setter String projectSourceType;

    @Getter@Setter Boolean isPipelineBuild;

    //These messages are used in the Jenkins console log.
    public static final String authorizationError = "Authorization error";
    public static final String configuredImproperlyError = "CodeBuild configured improperly in project settings";
    public static final String s3BucketBaseURL = "https://console.aws.amazon.com/s3/buckets/";
    public static final String envVariableSyntaxError = "CodeBuild environment variable keys and values cannot be empty and the string must be of the form [{key, value}, {key2, value2}]";
    public static final String envVariableNameSpaceError = "CodeBuild environment variable keys cannot start with CODEBUILD_";
    public static final String invalidProjectError = "Please select a project with S3 source type";
    public static final String notVersionsedS3BucketError = "A versioned S3 bucket is required.\n";


    @DataBoundConstructor
    public CodeBuilder(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, String awsSecretKey,
                       String region, String projectName, String sourceVersion, String sourceControlType,
                       String artifactTypeOverride, String artifactLocationOverride, String artifactNameOverride,
                       String artifactNamespaceOverride, String artifactPackagingOverride, String artifactPathOverride,
                       String envVariables, String buildSpecFile, String buildTimeoutOverride) {

        this.credentialsType = Validation.sanitize(credentialsType);
        this.credentialsId = Validation.sanitize(credentialsId);
        this.proxyHost = Validation.sanitize(proxyHost);
        this.proxyPort = Validation.sanitize(proxyPort);
        this.awsAccessKey = Validation.sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.region = Validation.sanitize(region);
        this.projectName = Validation.sanitize(projectName);
        this.sourceControlType = Validation.sanitize(sourceControlType);
        this.sourceVersion = Validation.sanitize(sourceVersion);
        this.artifactTypeOverride = Validation.sanitize(artifactTypeOverride);
        this.artifactLocationOverride = Validation.sanitize(artifactLocationOverride);
        this.artifactNameOverride = Validation.sanitize(artifactNameOverride);
        this.artifactNamespaceOverride = Validation.sanitize(artifactNamespaceOverride);
        this.artifactPackagingOverride = Validation.sanitize(artifactPackagingOverride);
        this.artifactPathOverride = Validation.sanitize(artifactPathOverride);
        this.envVariables = Validation.sanitize(envVariables);
        this.buildSpecFile = Validation.sanitize(buildSpecFile);
        this.buildTimeoutOverride = Validation.sanitize(buildTimeoutOverride);
        this.codeBuildResult = new CodeBuildResult();
        this.isPipelineBuild = false;
    }

    /*
     * This is the Jenkins method that executes the actual build.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        AWSClientFactory awsClientFactory;
        try {
            awsClientFactory = new AWSClientFactory(this.credentialsType, this.credentialsId, this.proxyHost, this.proxyPort,
                this.awsAccessKey, this.awsSecretKey, this.region);
        } catch (Exception e) {
            failBuild(build, listener, authorizationError, e.getMessage());
            return;
        }
        String projectConfigError = Validation.checkCodeBuilderConfig(this);
        if(!projectConfigError.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, projectConfigError);
        }

        String overridesErrorMessage = Validation.checkCodeBuilderStartBuildOverridesConfig(this);
        if(!overridesErrorMessage.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, overridesErrorMessage);
            return;
        }

        Collection<EnvironmentVariable> codeBuildEnvVars = null;
        try {
            codeBuildEnvVars = mapEnvVariables(envVariables);
        } catch(InvalidInputException e) {
            failBuild(build, listener, configuredImproperlyError, e.getMessage());
            return;
        }
        if(Validation.envVariablesHaveRestrictedPrefix(codeBuildEnvVars)) {
            failBuild(build, listener, configuredImproperlyError, envVariableNameSpaceError);
            return;
        }

        LoggingHelper.log(listener, awsClientFactory.getCredentialsDescriptor());

        final AWSCodeBuildClient cbClient;
        try {
            cbClient = awsClientFactory.getCodeBuildClient();
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        try {
            retrieveArtifactAndSourceInfo(cbClient);
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        StartBuildRequest startBuildRequest = new StartBuildRequest().withProjectName(this.projectName).
                withEnvironmentVariablesOverride(codeBuildEnvVars).withBuildspecOverride(this.buildSpecFile).
                withTimeoutInMinutesOverride(Validation.parseInt(this.buildTimeoutOverride));

        ProjectArtifacts artifactsOverride = generateStartBuildArtifactOverride();
        if(artifactsOverride != null) {
            startBuildRequest.setArtifactsOverride(artifactsOverride);
        }

        if(SourceControlType.JenkinsSource.toString().equals(sourceControlType)) {
            if(! Validation.checkSourceTypeS3(this.projectSourceType)) {
                failBuild(build, listener, invalidProjectError, "");
                return;
            }

            String sourceS3Bucket = Utils.getS3BucketFromObjectArn(this.projectSourceLocation);
            String sourceS3Key = Utils.getS3KeyFromObjectArn(this.projectSourceLocation);
            if(! Validation.checkBucketIsVersioned(sourceS3Bucket, awsClientFactory)) {
                failBuild(build, listener, notVersionsedS3BucketError, "");
                return;
            }

            S3DataManager s3DataManager = new S3DataManager(awsClientFactory.getS3Client(), sourceS3Bucket, sourceS3Key);
            String uploadedSourceVersion = "";

            try {
                UploadToS3Output uploadToS3Output = s3DataManager.uploadSourceToS3(listener, ws);
                // Override source version to object version id returned by S3
                if(uploadToS3Output.getObjectVersionId() != null) {
                    uploadedSourceVersion = uploadToS3Output.getObjectVersionId();
                } else {
                    failBuild(build, listener, notVersionsedS3BucketError, "");
                    return;
                }
                LoggingHelper.log(listener, "S3 object version id for uploaded source is " + uploadedSourceVersion);
            } catch (Exception e) {
                failBuild(build, listener, e.getMessage(), "");
                return;
            }

            startBuildRequest.setSourceVersion(uploadedSourceVersion);
            logStartBuildMessage(listener, uploadedSourceVersion);

        } else {
            startBuildRequest.setSourceVersion(this.sourceVersion);
            logStartBuildMessage(listener, sourceVersion);
        }

        final StartBuildResult sbResult;
        try {
            sbResult = cbClient.startBuild(startBuildRequest);
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        Build currentBuild;
        String buildId = sbResult.getBuild().getId();
        LoggingHelper.log(listener, "Build Id: " + buildId);

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

                    //only need to set these once, the others will need to be updated below as the build progresses.
                    String buildARN = currentBuild.getArn();
                    codeBuildResult.setBuildInformation(currentBuild.getId(), buildARN);
                    BuildArtifacts artifacts = currentBuild.getArtifacts();
                    codeBuildResult.setArtifactsLocation(artifacts != null ? artifacts.getLocation() : null);
                    action.setBuildId(buildId);
                    action.setBuildARN(buildARN);
                    action.setStartTime(currentBuild.getStartTime().toString());
                    action.setS3ArtifactURL(generateS3ArtifactURL(this.s3BucketBaseURL, artifactLocation, artifactType));
                    action.setArtifactTypeOverride(this.artifactTypeOverride);
                    action.setCodeBuildDashboardURL(generateDashboardURL(buildId));
                    action.setS3BucketName(artifactLocation);
                    action.setLogs(new ArrayList());

                    build.addAction(action);
                    haveInitializedAction = true;
                }

                updateDashboard(currentBuild, action, logMonitor, listener);
                Thread.sleep(5000L);

            } catch(Exception e) {
                if(e.getClass().equals(InterruptedException.class)) {
                    //Request to stop Jenkins build has been made. First make sure the build is stoppable
                    List<Build> buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();
                    currentBuild = buildsForId.get(0);
                    if(currentBuild.getBuildStatus().equals(StatusType.IN_PROGRESS.toString())) {
                        cbClient.stopBuild(new StopBuildRequest().withId(buildId));
                        //Wait for the build to actually stop
                        do {
                            buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();
                            currentBuild = buildsForId.get(0);
                            Thread.sleep(5000L);
                            logMonitor.pollForLogs(listener);
                            updateDashboard(currentBuild, action, logMonitor, listener);
                        } while(!currentBuild.getBuildStatus().equals(StatusType.STOPPED.toString()));
                        if(action != null) {
                            action.setJenkinsBuildSucceeds(false);
                        }
                        if(isPipelineBuild) {
                            this.codeBuildResult.setStopped();
                        } else {
                            build.setResult(Result.FAILURE);
                        }
                        return;
                    }
                } else {
                    if(action != null) {
                        action.setJenkinsBuildSucceeds(false);
                    }
                    failBuild(build, listener, e.getMessage(), "");
                    return;
                }
            }
        } while(currentBuild.getBuildStatus().equals(StatusType.IN_PROGRESS.toString()));

        if(currentBuild.getBuildStatus().equals(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH))) {
            action.setJenkinsBuildSucceeds(true);
            if(isPipelineBuild) {
                this.codeBuildResult.setSuccess();
            } else {
                build.setResult(Result.SUCCESS);
            }
        } else {
            action.setJenkinsBuildSucceeds(false);
            String errorMessage = "Build " + currentBuild.getId() + " failed" + "\n\t> " + action.getPhaseErrorMessage();
            failBuild(build, listener, errorMessage, "");
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
            logMonitor.setLogsLocation(b.getLogs());

            logMonitor.pollForLogs(listener);
            action.updateLogs(logMonitor.getLatestLogs());

            action.setPhases(b.getPhases());
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

    private void logStartBuildMessage(TaskListener listener, String sourceVersion) {
        StringBuilder message = new StringBuilder().append("Starting build with \n\t> project name " + projectName);
        if(!sourceVersion.isEmpty()) {
            message.append("\n\t> source version: " + sourceVersion);
        }
        if(!artifactTypeOverride.isEmpty()) {
            message.append("\n\t> artifact type: " + artifactTypeOverride);
        }
        if(!artifactLocationOverride.isEmpty()) {
            message.append("\n\t> artifact location: " + artifactLocationOverride);
        }
        if(!artifactNameOverride.isEmpty()) {
            message.append("\n\t> artifact name: " + artifactNameOverride);
        }
        if(!artifactNamespaceOverride.isEmpty()) {
            message.append("\n\t> artifact namespace: " + artifactNamespaceOverride);
        }
        if(!artifactPackagingOverride.isEmpty()) {
            message.append("\n\t> artifact packaging: " + artifactPackagingOverride);
        }
        if(!artifactPathOverride.isEmpty()) {
            message.append("\n\t> artifact path: " + artifactPathOverride);
        }
        if(!buildSpecFile.isEmpty()) {
            message.append("\n\t> build spec: " + buildSpecFile);
        }
        if(!envVariables.isEmpty()) {
            message.append("\n\t> environment variables: " + envVariables);
        }
        if(!buildTimeoutOverride.isEmpty()) {
            message.append("\n\t> build timeout: " + buildTimeoutOverride);
        }
        LoggingHelper.log(listener, message.toString());
    }

    private ProjectArtifacts generateStartBuildArtifactOverride() {
        ProjectArtifacts artifacts = new ProjectArtifacts();
        boolean overridesSpecified = false;
        if(!this.artifactTypeOverride.isEmpty()) {
            artifacts.setType(this.artifactTypeOverride);
            overridesSpecified = true;
        }
        if(!this.artifactLocationOverride.isEmpty()) {
            artifacts.setLocation(this.artifactLocationOverride);
            overridesSpecified = true;
        }
        if(!this.artifactNameOverride.isEmpty()) {
            artifacts.setName(this.artifactNameOverride);
            overridesSpecified = true;
        }
        if(!this.artifactNamespaceOverride.isEmpty()) {
            artifacts.setNamespaceType(this.artifactNamespaceOverride);
            overridesSpecified = true;
        }
        if(!this.artifactPackagingOverride.isEmpty()) {
            artifacts.setPackaging(this.artifactPackagingOverride);
            overridesSpecified = true;
        }
        if(!this.artifactPathOverride.isEmpty()) {
            artifacts.setPath(this.artifactPathOverride);
            overridesSpecified = true;
        }
        return overridesSpecified ? artifacts : null;
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
    // Throws an InvalidInputException when the input string doesn't match the form described in mapEnvVariables
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

    private void failBuild(Run<?, ?> build, TaskListener listener, String errorMessage, String secondaryError) throws AbortException {
        if(isPipelineBuild) {
            this.codeBuildResult.setFailure(errorMessage, secondaryError);
        } else {
            build.setResult(Result.FAILURE);
        }
        LoggingHelper.log(listener, errorMessage, secondaryError);
    }

    //// Jenkins-specific functions ////
    // all for CodeBuilder/config.jelly
    public String sourceControlTypeEquals(String given) {
        return String.valueOf((sourceControlType != null) && (sourceControlType.equals(given)));
    }

    public String credentialsTypeEquals(String given) {
        return String.valueOf((credentialsType != null) && (credentialsType.equals(given)));
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

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillArtifactTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactsType t: ArtifactsType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactNamespaceOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactNamespace t: ArtifactNamespace.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactPackagingOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ArtifactPackaging t : ArtifactPackaging.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(CodeBuildRegions r: CodeBuildRegions.values()) {
                selections.add(r.toString());
            }
            return selections;
        }

        public ListBoxModel doFillCredentialsIdItems() {
            final ListBoxModel selections = new ListBoxModel();

            SystemCredentialsProvider s = SystemCredentialsProvider.getInstance();
            for (Credentials c: s.getCredentials()) {
                if (c instanceof CodeBuildCredentials) {
                    selections.add(((CodeBuildCredentials) c).getId());
                }
            }
            return selections;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return "AWS CodeBuild";
        }
    }
}

