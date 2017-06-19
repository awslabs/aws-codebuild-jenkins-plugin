import java.io.Serializable;

public class CodeBuildResult implements Serializable {
    public static String IN_PROGRESS = "IN_PROGRESS";
    public static String SUCCESS = "SUCCESS";
    public static String FAILURE = "FAILURE";
    private String status = IN_PROGRESS;
    private String errorMessage;

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setFailure(String errorMessage){
        this.status = FAILURE;
        this.errorMessage = errorMessage;
    }

    public void setSuccess() {
        this.status = SUCCESS;
    }
}
