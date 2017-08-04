import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.io.Serializable;

public class CodeBuildResult implements Serializable {
    public static final long serialVersionUID = 23L;

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    public static final String STOPPED = "STOPPED";

    private String status = IN_PROGRESS;
    private String errorMessage;
    private String buildId;
    private String arn;
    private String artifactsLocation;


    @Whitelisted
    public String getStatus() {
        return status;
    }

    @Whitelisted
    public String getErrorMessage() {
        return errorMessage;
    }

    @Whitelisted
    public String getBuildId() {
        return buildId;
    }

    @Whitelisted
    public String getArn() {
        return arn;
    }

    @Whitelisted
    public String getArtifactsLocation() { return artifactsLocation; }

    public void setFailure(String errorMessage){
        this.status = FAILURE;
        this.errorMessage = errorMessage;
    }

    public void setSuccess() {
        this.status = SUCCESS;
    }

    public void setStopped() {
        this.status = STOPPED;
    }

    public void setBuildInformation(String buildId, String arn) {
        this.buildId = buildId;
        this.arn = arn;
    }

    public void setArtifactsLocation(String artifactsLocation) {
        this.artifactsLocation = artifactsLocation;
    }
}
