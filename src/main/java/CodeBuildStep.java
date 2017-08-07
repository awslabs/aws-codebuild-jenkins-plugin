/*
 *  Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import com.google.inject.Inject;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class CodeBuildStep extends AbstractStepImpl {

    private String proxyHost;
    private String proxyPort;
    private String awsAccessKey;
    private String awsSecretKey;
    private String region;
    private String projectName;
    private String sourceVersion;
    private String sourceControlType;
    private String artifactTypeOverride;
    private String artifactLocationOverride;
    private String artifactNameOverride;
    private String artifactNamespaceOverride;
    private String artifactPackagingOverride;
    private String artifactPathOverride;
    private String envVariables;
    private String buildSpecFile;
    private String buildTimeoutOverride;

    public String getProxyHost() {
        return proxyHost;
    }

    @DataBoundSetter
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    @DataBoundSetter
    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    @DataBoundSetter
    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    @DataBoundSetter
    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundConstructor
    public CodeBuildStep(String projectName) {
        this.projectName = projectName;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    @DataBoundSetter
    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getSourceControlType() {
        return sourceControlType;
    }

    @DataBoundSetter
    public void setSourceControlType(String sourceControlType) {
        this.sourceControlType = sourceControlType;
    }


    public String getArtifactTypeOverride() {
        return artifactTypeOverride;
    }

    @DataBoundSetter
    public void setArtifactTypeOverride(String artifactTypeOverride) {
        this.artifactTypeOverride = artifactTypeOverride;
    }

    public String getArtifactLocationOverride() {
        return artifactLocationOverride;
    }

    @DataBoundSetter
    public void setArtifactLocationOverride(String artifactLocationOverride) {
        this.artifactLocationOverride = artifactLocationOverride;
    }

    public String getArtifactNameOverride() {
        return this.artifactNameOverride;
    }

    @DataBoundSetter
    public void setArtifactNameOverride(String artifactNameOverride) {
        this.artifactNameOverride = artifactNameOverride;
    }

    public String getArtifactNamespaceOverride() {
        return this.artifactNamespaceOverride;
    }

    @DataBoundSetter
    public void setArtifactNamespaceOverride(String artifactNamespaceOverride) {
        this.artifactNamespaceOverride = artifactNamespaceOverride;
    }

    public String getArtifactPackagingOverride() {
        return this.artifactPackagingOverride;
    }

    @DataBoundSetter
    public void setArtifactPackagingOverride(String artifactPackagingOverride) {
        this.artifactPackagingOverride = artifactPackagingOverride;
    }

    public String getArtifactPathOverride() {
        return this.artifactPathOverride;
    }

    @DataBoundSetter
    public void setArtifactPathOverride(String artifactPathOverride) {
        this.artifactPathOverride = artifactPathOverride;
    }

    public String getEnvVariables() {
        return envVariables;
    }

    @DataBoundSetter
    public void setEnvVariables(String envVariables) {
        this.envVariables = envVariables;
    }

    public String getBuildSpecFile() {
        return buildSpecFile;
    }

    @DataBoundSetter
    public void setBuildSpecFile(String buildSpecFile) {
        this.buildSpecFile = buildSpecFile;
    }

    public String getBuildTimeoutOverride() {
        return buildTimeoutOverride;
    }

    @DataBoundSetter
    public void setBuildTimeoutOverride(String buildTimeoutOverride) {
        this.buildTimeoutOverride = buildTimeoutOverride;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(CodeBuildExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "awsCodeBuild";
        }

        @Override
        public String getDisplayName() {
            return "Invoke an AWS CodeBuild build";
        }
    }

    public static final class CodeBuildExecution extends AbstractSynchronousNonBlockingStepExecution<CodeBuildResult> {

        private static final long serialVersionUID = 1L;

        @Inject
        private transient CodeBuildStep step;

        @StepContextParameter
        private transient Run run;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient TaskListener listener;

        @Override
        protected CodeBuildResult run() throws Exception {
            CodeBuilder builder = new CodeBuilder(
                    step.getProxyHost(), step.getProxyPort(),
                    step.getAwsAccessKey(), step.getAwsSecretKey(),
                    step.getRegion(),
                    step.getProjectName(),
                    step.sourceVersion, step.sourceControlType,
                    step.artifactTypeOverride, step.artifactLocationOverride, step.artifactNameOverride,
                    step.artifactNamespaceOverride, step.artifactPackagingOverride, step.artifactPathOverride,
                    step.envVariables, step.buildSpecFile, step.buildTimeoutOverride
            );
            builder.setIsPipelineBuild(true);
            builder.perform(run, ws, launcher, listener);

            CodeBuildResult result = builder.getCodeBuildResult();

            if(result.getStatus().equals(CodeBuildResult.FAILURE)) {
                throw new CodeBuildException(result);
            }

            return result;
        }

        private void readObject(java.io.ObjectInputStream stream) throws java.io.IOException, ClassNotFoundException {
            throw new java.io.NotSerializableException(getClass().getName());
        }
    }
}
