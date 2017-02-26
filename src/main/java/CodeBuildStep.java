import com.google.inject.Inject;
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

    public static final class CodeBuildExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

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
        protected Void run() throws Exception {
            CodeBuilder builder = new CodeBuilder(
                    step.getProxyHost(), step.getProxyPort(),
                    step.getAwsAccessKey(), step.getAwsSecretKey(),
                    step.getRegion(),
                    step.getProjectName(),
                    step.sourceVersion, step.sourceControlType
            );
            builder.perform(run, ws, launcher, listener);
            return null;
        }

        private void readObject(java.io.ObjectInputStream stream) throws java.io.IOException, ClassNotFoundException {
            throw new java.io.NotSerializableException(getClass().getName());
        }
    }
}
