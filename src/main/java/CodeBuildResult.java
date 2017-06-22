import java.io.Serializable;

public class CodeBuildResult implements Serializable {
    public static final long serialVersionUID = 23L;

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
    
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
