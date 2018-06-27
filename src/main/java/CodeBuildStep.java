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

import com.amazonaws.services.codebuild.model.*;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.google.inject.Inject;
import enums.CodeBuildRegions;
import enums.EncryptionAlgorithm;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import lombok.Getter;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodeBuildStep extends AbstractStepImpl {

    @Getter private String credentialsType;
    @Getter private String credentialsId;
    @Getter private String proxyHost;
    @Getter private String proxyPort;
    @Getter private String awsAccessKey;
    @Getter private String awsSecretKey;
    @Getter private String awsSessionToken;
    @Getter private String region;
    @Getter private String projectName;
    @Getter private String sourceControlType;
    @Getter private String sourceVersion;
    @Getter private String sseAlgorithm;
    @Getter private String gitCloneDepthOverride;
    @Getter private String artifactTypeOverride;
    @Getter private String artifactLocationOverride;
    @Getter private String artifactNameOverride;
    @Getter private String artifactNamespaceOverride;
    @Getter private String artifactPackagingOverride;
    @Getter private String artifactPathOverride;
    @Getter private String environmentTypeOverride;
    @Getter private String imageOverride;
    @Getter private String computeTypeOverride;
    @Getter private String certificateOverride;
    @Getter private String cacheTypeOverride;
    @Getter private String cacheLocationOverride;
    @Getter private String serviceRoleOverride;
    @Getter private String privilegedModeOverride;
    @Getter private String sourceTypeOverride;
    @Getter private String sourceLocationOverride;
    @Getter private String insecureSslOverride;
    @Getter private String envVariables;
    @Getter private String envParameters;
    @Getter private String buildSpecFile;
    @Getter private String buildTimeoutOverride;

    @DataBoundSetter
    public void setCredentialsType(String credentialsType) {
        this.credentialsType = credentialsType;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    @DataBoundSetter
    public void setProxyPort(String proxyPort) {
        this.proxyPort = proxyPort;
    }

    @DataBoundSetter
    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }

    @DataBoundSetter
    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    @DataBoundSetter
    public void setAwsSessionToken(String awsSessionToken) {
        this.awsSessionToken = awsSessionToken;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
    }

    @DataBoundConstructor
    public CodeBuildStep(String projectName) {
        this.projectName = projectName;
    }

    @DataBoundSetter
    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    @DataBoundSetter
    public void setSseAlgorithm(String sseAlgorithm) {
        this.sseAlgorithm = sseAlgorithm;
    }

    @DataBoundSetter
    public void setSourceControlType(String sourceControlType) {
        this.sourceControlType = sourceControlType;
    }

    @DataBoundSetter
    public void setGitCloneDepthOverride(String gitCloneDepthOverride) { this.gitCloneDepthOverride = gitCloneDepthOverride; }

    @DataBoundSetter
    public void setArtifactTypeOverride(String artifactTypeOverride) {
        this.artifactTypeOverride = artifactTypeOverride;
    }

    @DataBoundSetter
    public void setArtifactLocationOverride(String artifactLocationOverride) {
        this.artifactLocationOverride = artifactLocationOverride;
    }

    @DataBoundSetter
    public void setArtifactNameOverride(String artifactNameOverride) {
        this.artifactNameOverride = artifactNameOverride;
    }

    @DataBoundSetter
    public void setArtifactNamespaceOverride(String artifactNamespaceOverride) {
        this.artifactNamespaceOverride = artifactNamespaceOverride;
    }

    @DataBoundSetter
    public void setArtifactPackagingOverride(String artifactPackagingOverride) {
        this.artifactPackagingOverride = artifactPackagingOverride;
    }

    @DataBoundSetter
    public void setArtifactPathOverride(String artifactPathOverride) {
        this.artifactPathOverride = artifactPathOverride;
    }

    @DataBoundSetter
    public void setEnvironmentTypeOverride(String environmentTypeOverride) {
        this.environmentTypeOverride = environmentTypeOverride;
    }

    @DataBoundSetter
    public void setImageOverride(String imageOverride) {
        this.imageOverride = imageOverride;
    }

    @DataBoundSetter
    public void setComputeTypeOverride(String computeTypeOverride) {
        this.computeTypeOverride = computeTypeOverride;
    }

    @DataBoundSetter
    public void setCertificateOverride(String certificateOverride) {
        this.certificateOverride = certificateOverride;
    }

    @DataBoundSetter
    public void setCacheTypeOverride(String cacheTypeOverride) {
        this.cacheTypeOverride = cacheTypeOverride;
    }

    @DataBoundSetter
    public void setCacheLocationOverride(String cacheLocationOverride) {
        this.cacheLocationOverride = cacheLocationOverride;
    }

    @DataBoundSetter
    public void setServiceRoleOverride(String serviceRoleOverride) {
        this.serviceRoleOverride = serviceRoleOverride;
    }

    @DataBoundSetter
    public void setPrivilegedModeOverride(String privilegedModeOverride) {
        this.privilegedModeOverride = privilegedModeOverride;
    }

    @DataBoundSetter
    public void setSourceTypeOverride(String sourceTypeOverride) {
        this.sourceTypeOverride = sourceTypeOverride;
    }

    @DataBoundSetter
    public void setSourceLocationOverride(String sourceLocationOverride) {
        this.sourceLocationOverride = sourceLocationOverride;
    }

    @DataBoundSetter
    public void setInsecureSslOverride(String insecureSslOverride) {
        this.insecureSslOverride = insecureSslOverride;
    }

    @DataBoundSetter
    public void setEnvVariables(String envVariables) {
        this.envVariables = envVariables;
    }

    @DataBoundSetter
    public void setEnvParameters(String envParameters) {
        this.envParameters = envParameters;
    }

    @DataBoundSetter
    public void setBuildSpecFile(String buildSpecFile) {
        this.buildSpecFile = buildSpecFile;
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

        public ListBoxModel doFillGitCloneDepthOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            selections.add("1");
            selections.add("5");
            selections.add("25");
            selections.add("Full");
            selections.add("");

            return selections;
        }

        public ListBoxModel doFillPrivilegedModeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            selections.add("False");
            selections.add("True");
            selections.add("");

            return selections;
        }

        public ListBoxModel doFillInsecureSslOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            selections.add("False");
            selections.add("True");
            selections.add("");

            return selections;
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

        public ListBoxModel doFillSourceTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (SourceType t : SourceType.values()) {
                if(!t.equals(SourceType.CODEPIPELINE)) {
                    selections.add(t.toString());
                }
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillComputeTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ComputeType t : ComputeType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCacheTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (CacheType t : CacheType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillEnvironmentTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (EnvironmentType t : EnvironmentType.values()) {
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
            Set<String> displayCredentials = new HashSet();

            for (Credentials c: s.getCredentials()) {
                if (c instanceof CodeBuildCredentials) {
                    displayCredentials.add(((CodeBuildCredentials) c).getId());
                }
            }

            Jenkins instance = Jenkins.getInstance();
            if(instance != null) {
                List<Folder> folders = instance.getAllItems(Folder.class);
                for (Folder folder : folders) {
                    List<Credentials> creds = CredentialsProvider.lookupCredentials(Credentials.class, (Item) folder);
                    for (Credentials cred : creds) {
                        if (cred instanceof CodeBuildCredentials) {
                            displayCredentials.add(((CodeBuildCredentials) cred).getId());
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
            CodeBuilder builder = (CodeBuilder) new CodeBuilder(
                    step.getCredentialsType(), step.getCredentialsId(),
                    step.getProxyHost(), step.getProxyPort(),
                    step.getAwsAccessKey(), Secret.fromString(step.getAwsSecretKey()), step.getAwsSessionToken(),
                    step.getRegion(), step.getProjectName(),
                    step.sourceVersion, step.sseAlgorithm, step.sourceControlType, step.gitCloneDepthOverride,
                    step.artifactTypeOverride, step.artifactLocationOverride, step.artifactNameOverride,
                    step.artifactNamespaceOverride, step.artifactPackagingOverride, step.artifactPathOverride,
                    step.envVariables, step.envParameters, step.buildSpecFile, step.buildTimeoutOverride,
                    step.sourceTypeOverride, step.sourceLocationOverride, step.environmentTypeOverride,
                    step.imageOverride, step.computeTypeOverride, step.cacheTypeOverride, step.cacheLocationOverride,
                    step.certificateOverride, step.serviceRoleOverride, step.insecureSslOverride, step.privilegedModeOverride
            ).readResolve();
            builder.perform(run, ws, launcher, listener);

            CodeBuildResult result = builder.getCodeBuildResult();

            if(result.getStatus().equals(CodeBuildResult.FAILURE) || result.getStatus().equals(CodeBuildResult.STOPPED)) {
                throw new CodeBuildException(result);
            }

            return result;
        }

        private void readObject(java.io.ObjectInputStream stream) throws java.io.IOException, ClassNotFoundException {
            throw new java.io.NotSerializableException(getClass().getName());
        }
    }
}
