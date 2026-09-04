import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.core.retry.RetryUtils;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;

public class CodeBuildClientRetryCondition implements RetryCondition {

    public static final String HTTP_ERROR_MESSAGE = "Unable to execute HTTP request";

    // v1 -> v2 mapping: the old RetryPolicy.RetryCondition#shouldRetry(AmazonWebServiceRequest,
    // AmazonClientException, int) becomes RetryCondition#shouldRetry(RetryPolicyContext). Semantics
    // are preserved: retry on a throttling error (RetryUtils.isThrottlingException) or when the
    // request could not be executed against the service (HTTP transport error).
    @Override
    public boolean shouldRetry(RetryPolicyContext context) {
        SdkException e = context.exception();
        return RetryUtils.isThrottlingException(e)
                || (e != null && e.getMessage() != null && e.getMessage().contains(HTTP_ERROR_MESSAGE));
    }
}
