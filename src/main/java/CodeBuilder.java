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

import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.amazonaws.codebuild.jenkinsplugin.Validation;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;
import software.amazon.awssdk.services.codebuild.model.Build;
import software.amazon.awssdk.services.s3.S3Client;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import enums.*;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;

public class CodeBuilder extends Builder implements SimpleBuildStep {

    @Getter private String credentialsType;
    @Getter private String credentialsId;
    @Getter private String proxyHost;
    @Getter private String proxyPort;
    @Getter private String awsAccessKey;
    @Getter private Secret awsSecretKey;
    @Getter private String awsSessionToken;
    @Getter private String region;
    @Getter private String projectName;
    @Getter private String sourceControlType;
    @Getter private String localSourcePath;
    @Getter private String workspaceSubdir;
    @DataBoundSetter public String workspaceIncludes;
    @DataBoundSetter public String workspaceExcludes;
    @Getter private String sourceVersion;
    @Getter private String sseAlgorithm;
    @Getter private String gitCloneDepthOverride;
    @Getter private String reportBuildStatusOverride;
    @Getter private String secondarySourcesOverride;
    @Getter private String secondarySourcesVersionOverride;

    @Getter private String artifactTypeOverride;
    @Getter private String artifactLocationOverride;
    @Getter private String artifactNameOverride;
    @Getter private String artifactNamespaceOverride;
    @Getter private String artifactPackagingOverride;
    @Getter private String artifactPathOverride;
    @Getter private String artifactEncryptionDisabledOverride;
    @Getter private String overrideArtifactName;
    @Getter private String secondaryArtifactsOverride;

    @Getter private String environmentTypeOverride;
    @Getter private String imageOverride;
    @Getter private String computeTypeOverride;
    @Getter private String certificateOverride;
    @Getter private String cacheTypeOverride;
    @Getter private String cacheLocationOverride;
    @Getter private String cacheModesOverride;
    @Getter private String cloudWatchLogsStatusOverride;
    @Getter private String cloudWatchLogsGroupNameOverride;
    @Getter private String cloudWatchLogsStreamNameOverride;
    @Getter private String s3LogsStatusOverride;
    @Getter private String s3LogsEncryptionDisabledOverride;
    @Getter private String s3LogsLocationOverride;
    @Getter private String serviceRoleOverride;
    @Getter private String privilegedModeOverride;
    @Getter private String sourceTypeOverride;
    @Getter private String sourceLocationOverride;
    @Getter private String insecureSslOverride;
    @Getter private String envVariables;
    @Getter private String envParameters;
    @Getter private String buildSpecFile;
    @Getter private String buildTimeoutOverride;
    @Getter private String cwlStreamingDisabled;

    // Not final: XStream/readResolve() deserialization of a saved job config bypasses the
    // constructor, leaving this null. perform() and failBuild() below defensively initialize it
    // so an early failure (e.g. credential resolution) never NPEs while masking the real error.
    @Getter private CodeBuildResult codeBuildResult;
    @Getter private String exceptionFailureMode;
    @Getter private String downloadArtifacts;
    @Getter private String downloadArtifactsRelativePath;
    private EnvVars envVars;
    private StepContext stepContext;

    //These messages are used in the Jenkins console log.
    public static final String authorizationError = "Authorization error";
    public static final String configuredImproperlyError = "CodeBuild configured improperly in project settings";
    public static final String s3BucketBaseURL = "https://s3.console.aws.amazon.com/s3/buckets/";
    public static final String s3ARNPrefix = "arn:aws:s3:::";
    public static final String jenkinsSourceOverrideError = "When using Jenkins source, sourceTypeOverride must be S3 and you must specify both sourceTypeOverride and sourceLocationOverride";
    public static final String jenkinsSourceProjectSourceTypeError = "Please select a project with S3 source type";
    public static final String envVariableSyntaxError = "CodeBuild environment variable keys and values cannot be empty and the string must be of the form [{key, value}, {key2, value2}]";
    public static final String envVariableNameSpaceError = "CodeBuild environment variable keys cannot start with CODEBUILD_";
    public static final String notVersionsedS3BucketError = "A versioned S3 bucket is required.\n";
    public static final String invalidSecondarySourceArtifacts = "Invalid secondary source/artifacts";

    private int batchGetBuildsCalls;
    private DescriptorImpl descriptor;

    // Fix #5(c): cap the post-interrupt stop-wait loop (~2 minutes at 5s per iteration) so a build
    // that never reports COMPLETED after a StopBuild can't spin the polling thread forever.
    private static final int MAX_STOP_WAIT_ITERATIONS = 24;


    @DataBoundConstructor
    public CodeBuilder(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, Secret awsSecretKey, String awsSessionToken,
                       String region, String projectName, String sourceVersion, String sseAlgorithm, String sourceControlType, String localSourcePath, String workspaceSubdir, String gitCloneDepthOverride,
                       String reportBuildStatusOverride, String secondarySourcesOverride, String secondarySourcesVersionOverride, String artifactTypeOverride, String artifactLocationOverride, String artifactNameOverride, String artifactNamespaceOverride,
                       String artifactPackagingOverride, String artifactPathOverride, String artifactEncryptionDisabledOverride, String overrideArtifactName, String secondaryArtifactsOverride,
                       String envVariables, String envParameters, String buildSpecFile, String buildTimeoutOverride, String sourceTypeOverride,
                       String sourceLocationOverride, String environmentTypeOverride, String imageOverride, String computeTypeOverride,
                       String cacheTypeOverride, String cacheLocationOverride, String cacheModesOverride, String cloudWatchLogsStatusOverride, String cloudWatchLogsGroupNameOverride, String cloudWatchLogsStreamNameOverride,
                       String s3LogsStatusOverride, String s3LogsEncryptionDisabledOverride,  String s3LogsLocationOverride, String certificateOverride, String serviceRoleOverride,
                       String insecureSslOverride, String privilegedModeOverride, String cwlStreamingDisabled, String exceptionFailureMode, String downloadArtifacts, String downloadArtifactsRelativePath) {

        this.credentialsType = sanitize(credentialsType);
        this.credentialsId = sanitize(credentialsId);
        this.proxyHost = sanitize(proxyHost);
        this.proxyPort = sanitize(proxyPort);
        this.awsAccessKey = sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.awsSessionToken = sanitize(awsSessionToken);
        this.region = sanitize(region);
        this.projectName = sanitize(projectName);
        this.sourceControlType = sanitize(sourceControlType);
        this.localSourcePath = sanitize(localSourcePath);
        this.workspaceSubdir = sanitize(workspaceSubdir);
        this.sourceVersion = sanitize(sourceVersion);
        this.sseAlgorithm = sanitize(sseAlgorithm);
        this.gitCloneDepthOverride = sanitize(gitCloneDepthOverride);
        this.reportBuildStatusOverride = sanitize(reportBuildStatusOverride);
        this.secondarySourcesOverride = decodeJSON(sanitize(secondarySourcesOverride));
        this.secondarySourcesVersionOverride = decodeJSON(sanitize(secondarySourcesVersionOverride));
        this.artifactTypeOverride = sanitize(artifactTypeOverride);
        this.artifactLocationOverride = sanitize(artifactLocationOverride);
        this.artifactNameOverride = sanitize(artifactNameOverride);
        this.artifactNamespaceOverride = sanitize(artifactNamespaceOverride);
        this.artifactPackagingOverride = sanitize(artifactPackagingOverride);
        this.artifactPathOverride = sanitize(artifactPathOverride);
        this.artifactEncryptionDisabledOverride = sanitize(artifactEncryptionDisabledOverride);
        this.overrideArtifactName = sanitize(overrideArtifactName);
        this.secondaryArtifactsOverride = decodeJSON(sanitize(secondaryArtifactsOverride));
        this.sourceTypeOverride = sanitize(sourceTypeOverride);
        this.sourceLocationOverride = sanitize(sourceLocationOverride);
        this.environmentTypeOverride = sanitize(environmentTypeOverride);
        this.imageOverride = sanitize(imageOverride);
        this.computeTypeOverride = sanitize(computeTypeOverride);
        this.cacheTypeOverride = sanitize(cacheTypeOverride);
        this.cacheLocationOverride = sanitize(cacheLocationOverride);
        this.cacheModesOverride = sanitize(cacheModesOverride);
        this.cloudWatchLogsStatusOverride = sanitize(cloudWatchLogsStatusOverride);
        this.cloudWatchLogsGroupNameOverride = sanitize(cloudWatchLogsGroupNameOverride);
        this.cloudWatchLogsStreamNameOverride = sanitize(cloudWatchLogsStreamNameOverride);
        this.s3LogsStatusOverride = sanitize(s3LogsStatusOverride);
        this.s3LogsEncryptionDisabledOverride = sanitize(s3LogsEncryptionDisabledOverride);
        this.s3LogsLocationOverride = sanitize(s3LogsLocationOverride);
        this.certificateOverride = sanitize(certificateOverride);
        this.serviceRoleOverride = sanitize(serviceRoleOverride);
        this.envVariables = sanitize(envVariables);
        this.envParameters = sanitize(envParameters);
        this.buildSpecFile = sanitizeYAML(buildSpecFile);
        this.buildTimeoutOverride = sanitize(buildTimeoutOverride);
        this.insecureSslOverride = sanitize(insecureSslOverride);
        this.privilegedModeOverride = sanitize(privilegedModeOverride);
        this.cwlStreamingDisabled = sanitize(cwlStreamingDisabled);
        this.exceptionFailureMode = sanitize(exceptionFailureMode);
        this.downloadArtifacts = sanitize(downloadArtifacts);
        this.downloadArtifactsRelativePath = sanitize(downloadArtifactsRelativePath);
        this.codeBuildResult = new CodeBuildResult();
        this.batchGetBuildsCalls = 0;
    }

    protected Object readResolve() {
        credentialsType = sanitize(credentialsType);
        credentialsId = sanitize(credentialsId);
        proxyHost = sanitize(proxyHost);
        proxyPort = sanitize(proxyPort);
        awsAccessKey = sanitize(awsAccessKey);
        awsSessionToken = sanitize(awsSessionToken);
        region = sanitize(region);
        projectName = sanitize(projectName);
        sourceControlType = sanitize(sourceControlType);
        localSourcePath = sanitize(localSourcePath);
        workspaceSubdir = sanitize(workspaceSubdir);
        sourceVersion = sanitize(sourceVersion);
        sseAlgorithm = sanitize(sseAlgorithm);
        gitCloneDepthOverride = sanitize(gitCloneDepthOverride);
        reportBuildStatusOverride = sanitize(reportBuildStatusOverride);
        secondarySourcesOverride = decodeJSON(sanitize(secondarySourcesOverride));
        secondarySourcesVersionOverride = decodeJSON(sanitize(secondarySourcesVersionOverride));
        artifactTypeOverride = sanitize(artifactTypeOverride);
        artifactLocationOverride = sanitize(artifactLocationOverride);
        artifactNameOverride = sanitize(artifactNameOverride);
        artifactNamespaceOverride = sanitize(artifactNamespaceOverride);
        artifactPackagingOverride = sanitize(artifactPackagingOverride);
        artifactPathOverride = sanitize(artifactPathOverride);
        artifactEncryptionDisabledOverride = sanitize(artifactEncryptionDisabledOverride);
        overrideArtifactName = sanitize(overrideArtifactName);
        secondaryArtifactsOverride = decodeJSON(sanitize(secondaryArtifactsOverride));
        sourceTypeOverride = sanitize(sourceTypeOverride);
        sourceLocationOverride = sanitize(sourceLocationOverride);
        environmentTypeOverride = sanitize(environmentTypeOverride);
        imageOverride = sanitize(imageOverride);
        computeTypeOverride = sanitize(computeTypeOverride);
        cacheTypeOverride = sanitize(cacheTypeOverride);
        cacheLocationOverride = sanitize(cacheLocationOverride);
        cacheModesOverride = sanitize(cacheModesOverride);
        cloudWatchLogsStatusOverride = sanitize(cloudWatchLogsStatusOverride);
        cloudWatchLogsGroupNameOverride = sanitize(cloudWatchLogsGroupNameOverride);
        cloudWatchLogsStreamNameOverride = sanitize(cloudWatchLogsStreamNameOverride);
        s3LogsStatusOverride = sanitize(s3LogsStatusOverride);
        s3LogsEncryptionDisabledOverride = sanitize(s3LogsEncryptionDisabledOverride);
        s3LogsLocationOverride = sanitize(s3LogsLocationOverride);
        certificateOverride = sanitize(certificateOverride);
        serviceRoleOverride = sanitize(serviceRoleOverride);
        envVariables = sanitize(envVariables);
        envParameters = sanitize(envParameters);
        buildSpecFile = sanitizeYAML(buildSpecFile);
        buildTimeoutOverride = sanitize(buildTimeoutOverride);
        insecureSslOverride = sanitize(insecureSslOverride);
        privilegedModeOverride = sanitize(privilegedModeOverride);
        cwlStreamingDisabled = sanitize(cwlStreamingDisabled);
        exceptionFailureMode = sanitize(exceptionFailureMode);
        downloadArtifacts = sanitize(downloadArtifacts);
        downloadArtifactsRelativePath = sanitize(downloadArtifactsRelativePath);
        return this;
    }

    public void perform(@NonNull Run<?, ?> build, @NonNull FilePath ws, @NonNull Launcher launcher, @NonNull TaskListener listener, StepContext stepContext) throws InterruptedException, IOException {
        this.stepContext = stepContext;
        perform(build, ws, launcher, listener);
    }

    /*
     * This is the Jenkins method that executes the actual build.
     */
    @Override
    public void perform(@NonNull Run<?, ?> build, @NonNull FilePath ws, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
        // Ensure a result holder exists before any code that can throw. On the deserialized-job
        // path readResolve() does not run the constructor's initializer, so this may still be null
        // here; without this, an early failure (e.g. credentials) would NPE in failBuild() and
        // swallow the real error message.
        if (this.codeBuildResult == null) {
            this.codeBuildResult = new CodeBuildResult();
        }
        descriptor = getDescriptor();
        envVars = build.getEnvironment(listener);

        AWSClientFactory awsClientFactory;
        try {
            awsClientFactory = new AWSClientFactory(
                    getParameterized(this.credentialsType),
                    getParameterized(this.credentialsId),
                    getParameterized(this.proxyHost),
                    getParameterized(this.proxyPort),
                    getParameterized(this.awsAccessKey),
                    this.awsSecretKey,
                    getParameterized(this.awsSessionToken),
                    getParameterized(this.region),
                    build,
                    this.stepContext);
        } catch (Exception e) {
            failBuild(build, listener, authorizationError, e.getMessage());
            return;
        }

        String projectConfigError = CodeBuilderValidation.checkEssentialConfig(this);
        if(!projectConfigError.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, projectConfigError);
            return;
        }

        String overridesErrorMessage = CodeBuilderValidation.checkStartBuildOverridesConfig(this);
        if(!overridesErrorMessage.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, overridesErrorMessage);
            return;
        }

        Collection<EnvironmentVariable> codeBuildEnvVars = null;
        try {
            codeBuildEnvVars = mapEnvVariables(getParameterized(envVariables), EnvironmentVariableType.PLAINTEXT);
            codeBuildEnvVars.addAll(mapEnvVariables(getParameterized(envParameters), EnvironmentVariableType.PARAMETER_STORE));
        } catch(InvalidInputException e) {
            failBuild(build, listener, configuredImproperlyError, e.getMessage());
            return;
        }
        if(CodeBuilderValidation.envVariablesHaveRestrictedPrefix(codeBuildEnvVars)) {
            failBuild(build, listener, configuredImproperlyError, envVariableNameSpaceError);
            return;
        }

        LoggingHelper.log(listener, awsClientFactory.getCredentialsDescriptor());

        final CodeBuildClient cbClient;
        try {
            cbClient = awsClientFactory.getCodeBuildClient();
        } catch (Exception e) {
            failBuild(build, listener, "Error when constructing CodeBuild client: ", e.getMessage());
            return;
        }

        // v1 -> v2: StartBuildRequest is immutable in SDK v2, so the request is assembled with a
        // builder (conditional withers/setters become conditional builder calls) and built once
        // just before the StartBuild call.
        StartBuildRequest.Builder startBuildRequestBuilder = StartBuildRequest.builder().
                projectName(getParameterized(projectName)).
                environmentVariablesOverride(codeBuildEnvVars).buildspecOverride(getParameterized(buildSpecFile)).
                timeoutInMinutesOverride(parseInt(getParameterized(buildTimeoutOverride)));

        ProjectArtifacts artifactsOverride = generateStartBuildArtifactOverride();
        if(artifactsOverride != null) {
            startBuildRequestBuilder.artifactsOverride(artifactsOverride);
        }

        ProjectCache cacheOverride = generateStartBuildCacheOverride();
        if(cacheOverride != null) {
            startBuildRequestBuilder.cacheOverride(cacheOverride);
        }

        LogsConfig logsConfigOverride = generateStartBuildLogsConfigOverride();
        if(logsConfigOverride != null) {
            startBuildRequestBuilder.logsConfigOverride(logsConfigOverride);
        }

        if(!getParameterized(environmentTypeOverride).isEmpty()) {
            startBuildRequestBuilder.environmentTypeOverride(getParameterized(environmentTypeOverride));
        }

        if(!getParameterized(imageOverride).isEmpty()) {
            startBuildRequestBuilder.imageOverride(getParameterized(imageOverride));
        }

        if(!getParameterized(computeTypeOverride).isEmpty()) {
            startBuildRequestBuilder.computeTypeOverride(getParameterized(computeTypeOverride));
        }

        if(!getParameterized(certificateOverride).isEmpty()) {
            startBuildRequestBuilder.certificateOverride(getParameterized(certificateOverride));
        }

        if(!getParameterized(serviceRoleOverride).isEmpty()) {
            startBuildRequestBuilder.serviceRoleOverride(getParameterized(serviceRoleOverride));
        }

        if(!getParameterized(insecureSslOverride).isEmpty()) {
            startBuildRequestBuilder.insecureSslOverride(Boolean.parseBoolean(getParameterized(insecureSslOverride)));
        }

        if(!getParameterized(privilegedModeOverride).isEmpty()) {
            startBuildRequestBuilder.privilegedModeOverride(Boolean.parseBoolean(getParameterized(privilegedModeOverride)));
        }

        List<ProjectSource> secondarySources;
        List<ProjectSourceVersion> secondarySourceVersions;
        List<ProjectArtifacts> secondaryArtifacts;

        try {
            secondarySources = Utils.parseDataList(getParameterized(secondarySourcesOverride), ProjectSource.class);
            secondarySourceVersions = Utils.parseDataList(getParameterized(secondarySourcesVersionOverride), ProjectSourceVersion.class);
            secondaryArtifacts = Utils.parseDataList(getParameterized(secondaryArtifactsOverride), ProjectArtifacts.class);
        } catch (InvalidInputException e) {
            failBuild(build, listener, invalidSecondarySourceArtifacts, e.getMessage());
            return;
        }

        if(secondarySources != null && !secondarySources.isEmpty()) {
            startBuildRequestBuilder.secondarySourcesOverride(secondarySources);
        }

        if(secondarySourceVersions != null && !secondarySourceVersions.isEmpty()) {
            startBuildRequestBuilder.secondarySourcesVersionOverride(secondarySourceVersions);
        }

        if(secondaryArtifacts != null && !secondaryArtifacts.isEmpty()) {
            startBuildRequestBuilder.secondaryArtifactsOverride(secondaryArtifacts);
        }

        if(SourceControlType.JenkinsSource.toString().equals(getParameterized(sourceControlType))) {
            String buildSourceLocation = "";
            if(!getParameterized(sourceTypeOverride).isEmpty() || !getParameterized(sourceLocationOverride).isEmpty()) {
                if(!CodeBuilderValidation.checkJenkinsSourceOverrides(getParameterized(sourceTypeOverride), getParameterized(sourceLocationOverride))) {
                    failBuild(build, listener, configuredImproperlyError, jenkinsSourceOverrideError);
                    return;
                }

                buildSourceLocation = getParameterized(sourceLocationOverride);
            } else {
                try {
                    buildSourceLocation = retrieveProjectSourceInfo(cbClient);
                } catch(Exception e) {
                    failBuild(build, listener, configuredImproperlyError, e.getMessage());
                    return;
                }
            }

            String sourceS3Bucket = Utils.getS3BucketFromObjectArn(buildSourceLocation);
            String sourceS3Key = Utils.getS3KeyFromObjectArn(buildSourceLocation);
            if(!CodeBuilderValidation.checkBucketIsVersioned(sourceS3Bucket, awsClientFactory)) {
                failBuild(build, listener, notVersionsedS3BucketError, "");
                return;
            }

            S3DataManager s3DataManager = new S3DataManager(awsClientFactory.getS3Client(), sourceS3Bucket, sourceS3Key, getParameterized(sseAlgorithm), getParameterized(localSourcePath), getParameterized(workspaceSubdir), getParameterized(workspaceIncludes), getParameterized(workspaceExcludes));
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
                failBuild(build, listener, "Error when uploading source to S3: ", e.getMessage());
                return;
            }

            startBuildRequestBuilder.sourceVersion(uploadedSourceVersion);
            logStartBuildMessage(listener, uploadedSourceVersion);

        } else {
            startBuildRequestBuilder.sourceVersion(getParameterized(sourceVersion));
            startBuildRequestBuilder.gitCloneDepthOverride(generateStartBuildGitCloneDepthOverride());
            if(!getParameterized(reportBuildStatusOverride).isEmpty()) {
                startBuildRequestBuilder.reportBuildStatusOverride(Boolean.parseBoolean(getParameterized(reportBuildStatusOverride)));
            }

            logStartBuildMessage(listener, getParameterized(sourceVersion));
        }

        if(!getParameterized(sourceTypeOverride).isEmpty()) {
            startBuildRequestBuilder.sourceTypeOverride(getParameterized(sourceTypeOverride));
            SourceAuth auth = generateStartBuildSourceAuthOverride(getParameterized(sourceTypeOverride));
            if(auth != null) {
                startBuildRequestBuilder.sourceAuthOverride(auth);
            }
        }
        if(!getParameterized(sourceLocationOverride).isEmpty()) {
            startBuildRequestBuilder.sourceLocationOverride(getParameterized(sourceLocationOverride));
        }

        final StartBuildResponse sbResult;
        try {
            sbResult = cbClient.startBuild(startBuildRequestBuilder.build());
        } catch (Exception e) {
            failBuild(build, listener, "Error when calling CodeBuild StartBuild: ", e.getMessage());
            return;
        }

        Build currentBuild = Build.builder().buildStatus(StatusType.IN_PROGRESS).build();
        String buildId = sbResult.build().id();
        LoggingHelper.log(listener, "Build id: " + buildId);
        LoggingHelper.log(listener, "CodeBuild dashboard: " + generateDashboardURL(buildId));

        boolean haveInitializedAction = false;
        CodeBuildAction action = null;
        CloudWatchMonitor logMonitor = null;

        //poll buildResult for build status until it's complete.
        do {
            try {
                List<Build> buildsForId = cbClient.batchGetBuilds(BatchGetBuildsRequest.builder().ids(buildId).build()).builds();

                if(buildsForId.size() != 1) {
                    throw new Exception("Multiple builds mapped to this build id.");
                }

                currentBuild = buildsForId.get(0);
                if(!haveInitializedAction) {
                    logMonitor = new CloudWatchMonitor(awsClientFactory.getCloudWatchLogsClient(), Boolean.parseBoolean(getParameterized(cwlStreamingDisabled)));
                    action = new CodeBuildAction(build);

                    //only need to set these once, the others will need to be updated below as the build progresses.
                    String buildARN = currentBuild.arn();
                    codeBuildResult.setBuildInformation(currentBuild.id(), buildARN);

                    action.setBuildId(buildId);
                    action.setBuildARN(buildARN);
                    action.setStartTime(currentBuild.startTime().toString());

                    ProjectSource source = currentBuild.source();
                    if(source != null) {
                        action.setSourceType(source.typeAsString());
                        action.setSourceLocation(source.location());

                        if(currentBuild.sourceVersion() == null) {
                            action.setSourceVersion("");
                        } else {
                            action.setSourceVersion(currentBuild.sourceVersion());
                        }

                        Integer depth = source.gitCloneDepth();
                        if(depth == null || depth == 0) {
                            action.setGitCloneDepth("Full");
                        } else {
                            action.setGitCloneDepth(String.valueOf(depth));
                        }

                        Boolean status = source.reportBuildStatus();
                        if(status != null) {
                            action.setReportBuildStatus(String.valueOf(status));
                        }
                    }

                    action.setArtifactTypeOverride(getParameterized(artifactTypeOverride));
                    action.setCodeBuildDashboardURL(generateDashboardURL(buildId));
                    action.setLogs(new ArrayList());
                    action.setCloudWatchLogsURL("");
                    action.setS3LogsURL("");

                    build.addAction(action);
                    haveInitializedAction = true;
                }

                updateDashboard(currentBuild, action, logMonitor, listener);
                Thread.sleep(getSleepTime(descriptor));
            } catch(Exception e) {
                if(e.getClass().equals(InterruptedException.class)) {
                    //Request to stop Jenkins build has been made. First make sure the build is stoppable
                    List<Build> buildsForId = cbClient.batchGetBuilds(BatchGetBuildsRequest.builder().ids(buildId).build()).builds();
                    currentBuild = buildsForId.get(0);
                    // Constant-first, null-safe: currentPhase() can be null on a freshly re-fetched build.
                    if(!BuildPhaseType.COMPLETED.toString().equals(currentBuild.currentPhase())) {
                        cbClient.stopBuild(StopBuildRequest.builder().id(buildId).build());
                        //Wait for the build to actually stop, bounded so a stuck StopBuild can't loop forever.
                        int stopWaitIterations = 0;
                        do {
                            buildsForId = cbClient.batchGetBuilds(BatchGetBuildsRequest.builder().ids(buildId).build()).builds();
                            currentBuild = buildsForId.get(0);
                            Thread.sleep(5000L);
                            // logMonitor is null when the interrupt arrived before the first poll initialized it.
                            if (logMonitor != null) {
                                logMonitor.pollForLogs(listener);
                            }
                            updateDashboard(currentBuild, action, logMonitor, listener);
                        } while (!BuildPhaseType.COMPLETED.toString().equals(currentBuild.currentPhase())
                                && ++stopWaitIterations < MAX_STOP_WAIT_ITERATIONS);
                    }
                    if (action != null) {
                        action.setJenkinsBuildSucceeds(false);
                    }
                    this.codeBuildResult.setStopped();
                    build.setResult(Result.ABORTED);
                    return;
                } else if(e.getMessage() != null && e.getMessage().contains(CodeBuildClientRetryCondition.HTTP_ERROR_MESSAGE)) {
                    Thread.sleep(getSleepTime(descriptor));
                    continue;
                } else {
                    if (action != null) {
                        action.setJenkinsBuildSucceeds(false);
                    }
                    failBuild(build, listener, "Error while polling build: ", e.getMessage());
                    return;
                }
            }
        } while(currentBuild.buildStatusAsString().equals(StatusType.IN_PROGRESS.toString()));

        // Read artifacts location once the build is complete and artifact name finalized
        codeBuildResult.setArtifactsLocation(currentBuild.artifacts() != null ? currentBuild.artifacts().location() : null);

        // Download build artifacts
        if(downloadArtifacts.equalsIgnoreCase(Boolean.TRUE.toString())) {
            downloadArtifactsFromS3(listener, awsClientFactory.getS3Client(), currentBuild, this.getArtifactRoot(ws));
        }
        if(currentBuild.buildStatusAsString().equals(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH))) {
            action.setJenkinsBuildSucceeds(true);
            this.codeBuildResult.setSuccess();
            build.setResult(Result.SUCCESS);
        } else {
            action.setJenkinsBuildSucceeds(false);
            failBuild(build, listener, "Build " + currentBuild.id() + " failed", action.getPhaseErrorMessage());
        }
        return;
    }

    private void downloadArtifactsFromS3(@NonNull TaskListener listener, S3Client s3Client, Build build, String artifactRoot) {
        try {
            S3Downloader s3Downloader = new S3Downloader(s3Client);
            s3Downloader.downloadBuildArtifacts(listener, build, artifactRoot);
        } catch (Exception e) {
            LoggingHelper.log(listener, "Error while downloading S3 build artifact: " + e.getMessage());
        }
    }

    public String getArtifactRoot(FilePath ws) {
        StringBuilder destinationPath = new StringBuilder(ws.getRemote());
        return destinationPath.append(File.separatorChar).append(this.downloadArtifactsRelativePath).toString();
    }

    private int getSleepTime(DescriptorImpl desc) {
        // 5s + 1s per BatchGetBuilds call already made + jitter for concurrent builds
        int secondToMs = (int) TimeUnit.SECONDS.toMillis(1);
        int sleepTimeInMs = secondToMs*desc.getMinSleepTime() + secondToMs*batchGetBuildsCalls++;
        return Math.min(sleepTimeInMs, secondToMs*desc.getMaxSleepTime()) + ThreadLocalRandom.current().nextInt(secondToMs*desc.getSleepJitter());
    }

    // Calls BatchGetProjects to get the source metadata for the configured project.
    // Validates that the project source type is S3 and returns the source location.
    // @param cbClient: the CodeBuild client used by this build.
    private String retrieveProjectSourceInfo(CodeBuildClient cbClient) throws RuntimeException, InvalidInputException {
        BatchGetProjectsResponse bgpResult = cbClient.batchGetProjects(
                BatchGetProjectsRequest.builder().names(getParameterized(projectName)).build());
        if(bgpResult.projects().isEmpty()) {
            throw new RuntimeException("Project " + getParameterized(projectName) + " does not exist.");
        } else {
            String projectSourceLocation = bgpResult.projects().get(0).source().location();
            String projectSourceType = bgpResult.projects().get(0).source().typeAsString();
            if(!CodeBuilderValidation.checkSourceTypeS3(projectSourceType)) {
                throw InvalidInputException.builder().message(jenkinsSourceProjectSourceTypeError).build();
            }

            return projectSourceLocation;
        }
    }

    // Performs an update of build data to the codebuild dashboard.
    // @param action: the entity representing the dashboard.
    void updateDashboard(Build b, CodeBuildAction action, CloudWatchMonitor logMonitor, TaskListener listener) {
        if(action != null) {
            action.setCurrentStatus(b.buildStatusAsString());
            logMonitor.setLogsLocation(b.logs());
            logMonitor.pollForLogs(listener);
            action.updateLogs(logMonitor.getLatestLogs());

            action.setPhases(b.phases());
            // v2: artifacts() is null until the ARTIFACTS phase while the build is IN_PROGRESS;
            // mirror the null-guard used when reading the final artifacts location after the loop.
            String artifactLocation = b.artifacts() != null ? b.artifacts().location() : null;
            action.setS3ArtifactURL(generateS3ArtifactURL(artifactTypeOverride, artifactLocation));
            action.setS3BucketName(artifactLocation);

            if(logMonitor.getLogsLocation() != null) {
                LogsLocation logsLocation = logMonitor.getLogsLocation();

                if(logsLocation.groupName() != null && logsLocation.streamName() != null && logsLocation.deepLink() != null
                    && action.getCloudWatchLogsURL().equals("")) {
                    String cloudWatchLogsURL = logsLocation.deepLink();
                    action.setCloudWatchLogsURL(cloudWatchLogsURL);
                    LoggingHelper.log(listener, "CloudWatch dashboard: " + cloudWatchLogsURL);
                }

                if(logsLocation.s3DeepLink() != null && b.phases() != null && action.getS3LogsURL().equals("")) {
                    List<BuildPhase> phases = b.phases();
                    for(BuildPhase phase : phases) {
                        if(phase.phaseTypeAsString() != null && phase.phaseTypeAsString().equals(BuildPhaseType.UPLOAD_ARTIFACTS.toString())) {
                            if(phase.contexts() != null && phase.contexts().get(0) != null && phase.contexts().get(0).message() != null
                                && !phase.contexts().get(0).message().contains("Error uploading logs:")) {
                                String s3LogsURL = logsLocation.s3DeepLink();
                                action.setS3LogsURL(s3LogsURL);
                                LoggingHelper.log(listener, "S3 logs location: " + s3LogsURL);
                            }
                        }
                    }
                }
            }
        }
    }

    // @param artifactsTypeOverride: part of the plugin configuration
    // @param artifactLocation: an S3 ARN representing the artifact location for this specific build
    // @return: a URL to the S3 artifact for this build as long as the artifactsTypeOverride isn't NO_ARTIFACTS
    public String generateS3ArtifactURL(String artifactsTypeOverride, String artifactLocation) {
        if((artifactsTypeOverride != null && artifactsTypeOverride.equals(ArtifactsType.NO_ARTIFACTS.toString())) ||
           artifactLocation == null || artifactLocation.isEmpty()) {
            return "";
        } else {
            return new StringBuilder().append(s3BucketBaseURL).append(artifactLocation.replace(s3ARNPrefix, "")).toString();
        }
    }

    private String generateDashboardURL(String buildId) {
        return new StringBuilder()
            .append("https://")
            .append(getParameterized(region))
            .append(".console.aws.amazon.com/codesuite/codebuild/projects/")
            .append(getParameterized(projectName))
            .append("/build/")
            .append(getParameterized(buildId))
            .append("/log?region=")
            .append(region)
            .toString();
    }

    private void logStartBuildMessage(TaskListener listener, String sourceVersion) {
        StringBuilder message = new StringBuilder().append("Starting build with \n\t> project name: " + getParameterized(projectName));
        if(!SourceControlType.JenkinsSource.toString().equals(getParameterized(sourceControlType))) {
            if(!gitCloneDepthOverride.isEmpty()) {
                message.append("\n\t> git clone depth: " + getParameterized(gitCloneDepthOverride) + " (git clone depth is omitted when source provider is Amazon S3)");
            }
            if(!reportBuildStatusOverride.isEmpty()) {
                message.append("\n\t> report build status: " + getParameterized(reportBuildStatusOverride) + " (report build status is valid when source provider is GitHub)");
            }
        }
        if(!sourceTypeOverride.isEmpty()) {
            message.append("\n\t> source type: " + getParameterized(sourceTypeOverride));
        }
        if(!sourceLocationOverride.isEmpty()) {
            message.append("\n\t> source location: " + getParameterized(sourceLocationOverride));
        }
        if(!sourceVersion.isEmpty()) {
            message.append("\n\t> source version: " + sourceVersion);
        }
        if(!secondarySourcesOverride.isEmpty()) {
            message.append("\n\t> secondary source overrides: " + getParameterized(secondarySourcesOverride));
        }
        if(!secondarySourcesVersionOverride.isEmpty()) {
            message.append("\n\t> secondary source version overrides: " + getParameterized(secondarySourcesVersionOverride));
        }
        if(!artifactTypeOverride.isEmpty()) {
            message.append("\n\t> artifact type: " + getParameterized(artifactTypeOverride));
        }
        if(!artifactLocationOverride.isEmpty()) {
            message.append("\n\t> artifact location: " + getParameterized(artifactLocationOverride));
        }
        if(!artifactNameOverride.isEmpty()) {
            message.append("\n\t> artifact name: " + getParameterized(artifactNameOverride));
        }
        if(!overrideArtifactName.isEmpty()) {
            message.append("\n\t> overrideArtifactName: " + getParameterized(overrideArtifactName));
        }
        if(!artifactNamespaceOverride.isEmpty()) {
            message.append("\n\t> artifact namespace: " + getParameterized(artifactNamespaceOverride));
        }
        if(!artifactPackagingOverride.isEmpty()) {
            message.append("\n\t> artifact packaging: " + getParameterized(artifactPackagingOverride));
        }
        if(!artifactPathOverride.isEmpty()) {
            message.append("\n\t> artifact path: " + getParameterized(artifactPathOverride));
        }
        if(!artifactEncryptionDisabledOverride.isEmpty()) {
            message.append("\n\t> artifact encryption disabled: " + getParameterized(artifactEncryptionDisabledOverride));
        }
        if(!secondaryArtifactsOverride.isEmpty()) {
            message.append("\n\t> secondary artifact overrides: " + getParameterized(secondaryArtifactsOverride));
        }
        if(!envVariables.isEmpty()) {
            message.append("\n\t> environment variables: " + getParameterized(envVariables));
        }
        if(!buildTimeoutOverride.isEmpty()) {
            message.append("\n\t> build timeout: " + getParameterized(buildTimeoutOverride));
        }
        if(!cacheTypeOverride.isEmpty()) {
            message.append("\n\t> cache type: " + getParameterized(cacheTypeOverride));
        }
        if(!cacheLocationOverride.isEmpty()) {
            message.append("\n\t> cache location: " + getParameterized(cacheLocationOverride));
        }
        if(!cacheModesOverride.isEmpty()) {
            message.append("\n\t> cache modes: " + getParameterized(cacheModesOverride));
        }
        if(!cloudWatchLogsStatusOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs status: " + getParameterized(cloudWatchLogsStatusOverride));
        }
        if(!cloudWatchLogsGroupNameOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs group name: " + getParameterized(cloudWatchLogsGroupNameOverride));
        }
        if(!cloudWatchLogsStreamNameOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs stream name: " + getParameterized(cloudWatchLogsStreamNameOverride));
        }
        if(!s3LogsStatusOverride.isEmpty()) {
            message.append("\n\t> s3 logs status: " + getParameterized(s3LogsStatusOverride));
        }
        if(!s3LogsEncryptionDisabledOverride.isEmpty()) {
            message.append("\n\t> s3 logs encryption disabled: " + getParameterized(s3LogsEncryptionDisabledOverride));
        }
        if(!s3LogsLocationOverride.isEmpty()) {
            message.append("\n\t> s3 logs location: " + getParameterized(s3LogsLocationOverride));
        }
        if(!environmentTypeOverride.isEmpty()) {
            message.append("\n\t> environment type: " + getParameterized(environmentTypeOverride));
        }
        if(!imageOverride.isEmpty()) {
            message.append("\n\t> image: " + getParameterized(imageOverride));
        }
        if(!privilegedModeOverride.isEmpty()) {
            message.append("\n\t> privileged mode override: " + getParameterized(privilegedModeOverride));
        }
        if(!computeTypeOverride.isEmpty()) {
            message.append("\n\t> compute type: " + getParameterized(computeTypeOverride));
        }
        if(!insecureSslOverride.isEmpty()) {
            message.append("\n\t> insecure ssl override: " + getParameterized(insecureSslOverride));
        }
        if(!certificateOverride.isEmpty()) {
            message.append("\n\t> certificate: " + getParameterized(certificateOverride));
        }
        if(!serviceRoleOverride.isEmpty()) {
            message.append("\n\t> service role: " + getParameterized(serviceRoleOverride));
        }
        if(!cwlStreamingDisabled.isEmpty()) {
            message.append("\n\t> CloudWatch logs streaming disabled: " + getParameterized(cwlStreamingDisabled));
        }
        if(!exceptionFailureMode.isEmpty()) {
            message.append("\n\t> exception failure mode status: " + getParameterized(exceptionFailureMode));
        }
        if(!downloadArtifacts.isEmpty()) {
            message.append("\n\t> Download build artifacts: " + getParameterized(downloadArtifacts));
        }
        if(!downloadArtifactsRelativePath.isEmpty()) {
            message.append("\n\t> Download build artifacts relative path: " + getParameterized(downloadArtifactsRelativePath));
        }
        if(!buildSpecFile.isEmpty()) {
            message.append("\n\t> build spec: \n" + getParameterized(buildSpecFile));
        }

        LoggingHelper.log(listener, message.toString());
    }

    private Integer generateStartBuildGitCloneDepthOverride() {
        String depth = getParameterized(gitCloneDepthOverride);
        if(depth.isEmpty() || depth.equals("Full")) {
            return 0;
        }

        return Integer.parseInt(depth);
    }

    // v1 -> v2: ProjectArtifacts is immutable; setters become builder calls.
    private ProjectArtifacts generateStartBuildArtifactOverride() {
        ProjectArtifacts.Builder artifacts = ProjectArtifacts.builder();
        boolean overridesSpecified = false;
        if(!getParameterized(artifactTypeOverride).isEmpty()) {
            artifacts.type(getParameterized(artifactTypeOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactLocationOverride).isEmpty()) {
            artifacts.location(getParameterized(artifactLocationOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactNameOverride).isEmpty()) {
            artifacts.name(getParameterized(artifactNameOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactNamespaceOverride).isEmpty()) {
            artifacts.namespaceType(getParameterized(artifactNamespaceOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactPackagingOverride).isEmpty()) {
            artifacts.packaging(getParameterized(artifactPackagingOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactPathOverride).isEmpty()) {
            artifacts.path(getParameterized(artifactPathOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactEncryptionDisabledOverride).isEmpty()) {
            artifacts.encryptionDisabled(Boolean.parseBoolean(artifactEncryptionDisabledOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(overrideArtifactName).isEmpty()) {
            artifacts.overrideArtifactName(Boolean.parseBoolean(overrideArtifactName));
            overridesSpecified = true;
        }

        return overridesSpecified ? artifacts.build() : null;
    }

    // v1 -> v2: ProjectCache is immutable; setters become builder calls. The old
    // cache.getType().equals("LOCAL") read-back is done off a local variable since the v2 builder
    // is write-only (this also avoids the latent NPE when the type was never set).
    private ProjectCache generateStartBuildCacheOverride() {
        ProjectCache.Builder cache = ProjectCache.builder();
        boolean overridesSpecified = false;
        String cacheType = getParameterized(cacheTypeOverride);
        if(!cacheType.isEmpty()) {
            cache.type(cacheType);
            overridesSpecified = true;
        }
        List<String> cacheModes = listCacheModes(getParameterized(cacheModesOverride));
        if(!cacheModes.isEmpty() && cacheType.equals("LOCAL")) {
            cache.modesWithStrings(cacheModes);
            overridesSpecified = true;
        }
        if(!getParameterized(cacheLocationOverride).isEmpty()) {
            cache.location(getParameterized(cacheLocationOverride));
            overridesSpecified = true;
        }
        return overridesSpecified ? cache.build() : null;
    }

    // Given a String representing cache modes, returns a list of String
    // The input string must be in the form [LOCAL_DOCKER_LAYER_CACHE, LOCAL_CUSTOM_CACHE] or else empty list is returned
    public static List<String> listCacheModes(String cacheModes) {
        if(cacheModes == null || cacheModes.isEmpty()) {
            return Collections.emptyList();
        }
        cacheModes = cacheModes.replaceAll("^.|.$", "");
        return Arrays.asList(cacheModes.split("\\s*,\\s*"));
    }

    // v1 -> v2: LogsConfig/CloudWatchLogsConfig/S3LogsConfig are immutable; setters become builder calls.
    private LogsConfig generateStartBuildLogsConfigOverride() {
        CloudWatchLogsConfig.Builder cloudWatchLogsConfig = CloudWatchLogsConfig.builder();
        S3LogsConfig.Builder s3LogsConfig = S3LogsConfig.builder();

        boolean overridesCloudWatchLogsSpecified = false;
        boolean overridesS3LogsSpecified = false;

        if(!getParameterized(cloudWatchLogsStatusOverride).isEmpty()) {
            cloudWatchLogsConfig.status(getParameterized(cloudWatchLogsStatusOverride));
            overridesCloudWatchLogsSpecified = true;
        }
        if(!getParameterized(cloudWatchLogsGroupNameOverride).isEmpty()) {
            cloudWatchLogsConfig.groupName(getParameterized(cloudWatchLogsGroupNameOverride));
            overridesCloudWatchLogsSpecified = true;
        }
        if(!getParameterized(cloudWatchLogsStreamNameOverride).isEmpty()) {
            cloudWatchLogsConfig.streamName(getParameterized(cloudWatchLogsStreamNameOverride));
            overridesCloudWatchLogsSpecified = true;
        }

        if(!getParameterized(s3LogsStatusOverride).isEmpty()) {
            s3LogsConfig.status(getParameterized(s3LogsStatusOverride));
            overridesS3LogsSpecified = true;
        }
        if(!getParameterized(s3LogsEncryptionDisabledOverride).isEmpty()) {
            s3LogsConfig.encryptionDisabled(Boolean.parseBoolean(getParameterized(s3LogsEncryptionDisabledOverride)));
            overridesS3LogsSpecified = true;
        }
        if(!getParameterized(s3LogsLocationOverride).isEmpty()) {
            s3LogsConfig.location(getParameterized(s3LogsLocationOverride));
            overridesS3LogsSpecified = true;
        }

        LogsConfig.Builder logsConfig = LogsConfig.builder();
        if(overridesCloudWatchLogsSpecified) {
            logsConfig.cloudWatchLogs(cloudWatchLogsConfig.build());
        }
        if(overridesS3LogsSpecified) {
            logsConfig.s3Logs(s3LogsConfig.build());
        }

        return overridesCloudWatchLogsSpecified || overridesS3LogsSpecified ? logsConfig.build() : null;
    }

    private SourceAuth generateStartBuildSourceAuthOverride(String sourceType) {
        SourceAuth auth = null;
        if(sourceType.equals(SourceType.GITHUB.toString()) || sourceType.equals(SourceType.BITBUCKET.toString())) {
            auth = SourceAuth.builder().type(SourceAuthType.OAUTH.toString()).build();
        }
        return auth;
    }

    // Given a String representing environment variables, returns a list of software.amazon.awssdk.services.codebuild.model.EnvironmentVariable
    // objects with the same data. The input string must be in the form [{Key, value}, {k2, v2}] or else null is returned
    public static Collection<EnvironmentVariable> mapEnvVariables(String envVars, EnvironmentVariableType envVarType) throws InvalidInputException {
        Collection<EnvironmentVariable> result = new HashSet<EnvironmentVariable>();
        if(envVars == null || envVars.isEmpty()) {
            return result;
        }

        envVars = envVars.replaceAll("\\}\\s*,\\s*\\{", "},{");
        envVars = envVars.replaceAll("\\[\\s*\\{", "[{");
        envVars = envVars.replaceAll("\\}\\s*\\]", "}]");
        envVars = envVars.replaceAll("[\\n|\\t]", "").trim();
        if(envVars.length() < 4 || envVars.charAt(0) != '[' || envVars.charAt(envVars.length()-1) != ']' ||
           envVars.charAt(1) != '{' || envVars.charAt(envVars.length()-2) != '}') {
            throw InvalidInputException.builder().message(envVariableSyntaxError).build();
        } else {
            envVars = envVars.substring(2, envVars.length()-2);
        }

        int numCommas = envVars.replaceAll("[^,]", "").length();
        if(numCommas == 0) {
            throw InvalidInputException.builder().message(envVariableSyntaxError).build();
        }
        //single environment variable case vs multiple
        if(numCommas == 1) {
            result.add(deserializeCodeBuildEnvVar(envVars, envVarType));
        } else {
            String[] evs = envVars.split("\\},\\{");
            for(int i = 0; i < evs.length; i++) {
                result.add(deserializeCodeBuildEnvVar(evs[i], envVarType));
            }
        }
        return result;
    }

    // Given a string of the form "key,value", returns a CodeBuild Environment Variable with that data.
    // Throws an InvalidInputException when the input string doesn't match the form described in mapEnvVariables
    private static EnvironmentVariable deserializeCodeBuildEnvVar(String ev, EnvironmentVariableType envVarType) throws InvalidInputException {
        if(ev.replaceAll("\\\\,", "").replaceAll("[^,]", "").length() != 1) {
            throw InvalidInputException.builder().message(envVariableSyntaxError).build();
        }

        String[] keyAndValue = ev.split("(?<!\\\\),");
        if(keyAndValue.length != 2 || keyAndValue[0].isEmpty() || keyAndValue[1].isEmpty()) {
            throw InvalidInputException.builder().message(envVariableSyntaxError).build();
        }
        return EnvironmentVariable.builder().name(keyAndValue[0].trim().replaceAll("\\\\,", ",")).value(keyAndValue[1].trim().replaceAll("\\\\,", ",")).type(envVarType).build();
    }

    private void failBuild(Run<?, ?> build, TaskListener listener, String errorMessage, String secondaryError) throws AbortException {
        // Defense in depth: if this instance was deserialized (readResolve() bypasses the
        // constructor) and perform()'s early init was somehow skipped, initialize here so the
        // underlying error below is recorded and logged instead of being masked by an NPE.
        if (this.codeBuildResult == null) {
            this.codeBuildResult = new CodeBuildResult();
        }
        this.codeBuildResult.setFailure(errorMessage, secondaryError);
        LoggingHelper.log(listener, errorMessage, secondaryError);

        if(!exceptionFailureMode.isEmpty() && getParameterized(exceptionFailureMode).equalsIgnoreCase(LogsConfigStatusType.ENABLED.toString())) {
            throw new AbortException(errorMessage + "\n\t> " + secondaryError);
        } else {
            build.setResult(Result.FAILURE);
        }
    }

    //Given a CodeBuild build parameter, checks if it contains any Jenkins parameters and if so, evaluates and returns the
    //value.
    public String getParameterized(String codeBuildParam) {
        String result = envVars.expand(codeBuildParam);
        if(result == null) {
          return "";
        }
        return result;
    }

    //// Jenkins-specific functions ////
    // all for CodeBuilder/config.jelly
    public String sourceControlTypeEquals(String given) {
        return String.valueOf((sourceControlType != null) && (sourceControlType.equals(given)));
    }

    public String credentialsTypeEquals(String given) {
        return String.valueOf((credentialsType != null) && (credentialsType.equals(given)));
    }

    public String downloadArtifactsEquals(String given) {
        return String.valueOf((downloadArtifacts != null) && (downloadArtifacts.equalsIgnoreCase(given)));
    }

    public static String decodeJSON(String json) {
        return json.replaceAll("&amp;quot;", "\"").replaceAll("&quot;", "\"").replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;","<").replaceAll("''", "'");
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
        private static final int DEFAULT_MIN_SLEEP_TIME = 3;
        private static final int DEFAULT_MAX_SLEEP_TIME = 60;
        private static final int DEFAULT_SLEEP_JITTER = 5;
        private static final int MAX_BUILD_DURATION = (int) TimeUnit.HOURS.toSeconds(8);


        private int minSleepTime;
        private int maxSleepTime;
        private int sleepJitter;

        public DescriptorImpl() {
            load();
        }

        public int getMinSleepTime() {
            if (minSleepTime <= 0) {
                return DEFAULT_MIN_SLEEP_TIME;
            } else {
                return minSleepTime;
            }
        }

        public int getMaxSleepTime() {
            if (minSleepTime <= 0 || maxSleepTime <= 0 || minSleepTime > maxSleepTime) {
                return DEFAULT_MAX_SLEEP_TIME;
            } else {
                return maxSleepTime;
            }
        }

        public int getSleepJitter() {
            if (sleepJitter <= 0) {
                return DEFAULT_SLEEP_JITTER;
            } else {
                return sleepJitter;
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            this.minSleepTime = formData.optInt("minSleepTime", 0);
            this.maxSleepTime = formData.optInt("maxSleepTime", 0);
            this.sleepJitter = formData.optInt("sleepJitter", 0);
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillGitCloneDepthOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(GitCloneDepth t: GitCloneDepth.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillReportBuildStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillPrivilegedModeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillInsecureSslOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillArtifactTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactsType t: ArtifactsType.values()) {
                if(t == ArtifactsType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactNamespaceOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactNamespace t: ArtifactNamespace.values()) {
                if(t == ArtifactNamespace.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactPackagingOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ArtifactPackaging t : ArtifactPackaging.values()) {
                if(t == ArtifactPackaging.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactEncryptionDisabledOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillOverrideArtifactNameItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillSourceTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (SourceType t : SourceType.values()) {
                if(t != SourceType.UNKNOWN_TO_SDK_VERSION && !t.equals(SourceType.CODEPIPELINE)) {
                    selections.add(t.toString());
                }
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillComputeTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ComputeType t : ComputeType.values()) {
                if(t == ComputeType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCacheTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (CacheType t : CacheType.values()) {
                if(t == CacheType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCloudWatchLogsStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(LogsConfigStatusType t : LogsConfigStatusType.values()) {
                if(t == LogsConfigStatusType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillS3LogsStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(LogsConfigStatusType t : LogsConfigStatusType.values()) {
                if(t == LogsConfigStatusType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillS3LogsEncryptionDisabledOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t : BooleanValue.values()) {
                selections.add(t.toString());
            }
            return selections;
        }

        public ListBoxModel doFillEnvironmentTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (EnvironmentType t : EnvironmentType.values()) {
                if(t == EnvironmentType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            // SECURITY-3773: require permission before enumerating credentials IDs
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return new StandardListBoxModel().includeCurrentValue(credentialsId);
                }
            } else if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }

            final ListBoxModel selections = new ListBoxModel();

            SystemCredentialsProvider s = SystemCredentialsProvider.getInstance();
            Set<String> displayCredentials = new HashSet<>();

            for (Credentials c: s.getCredentials()) {
                if (c instanceof CodeBuildBaseCredentials) {
                    displayCredentials.add(((CodeBuildBaseCredentials) c).getId());
                }
            }

            Jenkins instance = Jenkins.getInstance();
            if(instance != null) {
                List<Folder> folders = instance.getAllItems(Folder.class);
                for(Folder folder: folders) {
                    List<Credentials> creds = CredentialsProvider.lookupCredentials(Credentials.class, (Item) folder);
                    for(Credentials cred: creds) {
                        if (cred instanceof CodeBuildBaseCredentials) {
                            displayCredentials.add(((CodeBuildBaseCredentials) cred).getId());
                        }
                    }
                }
            }


            for(String credString: displayCredentials) {
                selections.add(credString);
            }

            return selections;
        }

        public ListBoxModel doFillSseAlgorithmItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(EncryptionAlgorithm e: EncryptionAlgorithm.values()) {
                selections.add(e.toString());
            }

            return selections;
        }

        public ListBoxModel doFillCwlStreamingDisabledItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillExceptionFailureModeItems() {
            final ListBoxModel selections = new ListBoxModel();

            // ENABLED/DISABLED
            for (LogsConfigStatusType t : LogsConfigStatusType.values()) {
                if(t == LogsConfigStatusType.UNKNOWN_TO_SDK_VERSION) continue;
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public FormValidation doCheckMaxSleepTime(@QueryParameter String minSleepTime, @QueryParameter String maxSleepTime, @QueryParameter String sleepJitter) {
            Integer min = 0;
            try {
                min = parseInt(minSleepTime);
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a positive integer");
            }
            if (min == null || min <= 0) {
                return FormValidation.error("Not a positive integer");
            } else if (min > MAX_BUILD_DURATION) {
                return FormValidation.error("Cannot be greater than 28800 (eight hours)");
            }

            Integer max = 0;
            try {
                max = parseInt(maxSleepTime);
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a positive integer");
            }

            if (max == null || max <= 0) {
                return FormValidation.error("Not a positive integer");
            } else if (max > MAX_BUILD_DURATION) {
                return FormValidation.error("Cannot be greater than 28800 (eight hours)");
            } else if (min > max) {
                return FormValidation.error("Must be greater than minimum interval");
            }

            Integer jitter = 0;
            try {
                jitter = parseInt(sleepJitter);
            } catch (NumberFormatException e) {
                return FormValidation.error("Not a positive integer");
            }
            if (jitter == null || jitter <= 0) {
                return FormValidation.error("Not a positive integer");
            } else if (max > MAX_BUILD_DURATION) {
                return FormValidation.error("Cannot be greater than 28800 (eight hours)");
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckS3LogsEncryptionDisabledOverride(@QueryParameter String s3LogsEncryptionDisabledOverride, @QueryParameter String s3LogsStatusOverride) {
            if (s3LogsStatusOverride == null || s3LogsStatusOverride.isEmpty()) {
                if (s3LogsEncryptionDisabledOverride != null && !s3LogsEncryptionDisabledOverride.isEmpty()) {
                    return FormValidation.error("'S3 Logs Status Override' value must be provided");
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckS3LogsLocationOverride(@QueryParameter String s3LogsLocationOverride, @QueryParameter String s3LogsStatusOverride) {
            if (!LogsConfigStatusType.ENABLED.toString().equals(s3LogsStatusOverride)) {
                if (s3LogsLocationOverride != null && !s3LogsLocationOverride.isEmpty()) {
                    return FormValidation.error("'S3 Logs Location Override' must be null if 'S3 Logs Status Override' is 'DISABLED' or empty");
                }
            } else {
                if (s3LogsLocationOverride == null || s3LogsLocationOverride.isEmpty()) {
                    return FormValidation.error("'S3 Logs Location Override' must be provided if 'S3 Logs Status Override' is 'ENABLED'");
                }
            }

            return FormValidation.ok();
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
