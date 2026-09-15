import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.RetryUtils;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;

public class CodeBuildClientRetryCondition implements RetryCondition {

    public static final String HTTP_ERROR_MESSAGE = "Unable to execute HTTP request";

    @Override
    public boolean shouldRetry(RetryPolicyContext context) {
        SdkException e = context.exception();
        return RetryUtils.isThrottlingException(e)
                || (e != null && e.getMessage() != null && e.getMessage().contains(HTTP_ERROR_MESSAGE));
    }
}
