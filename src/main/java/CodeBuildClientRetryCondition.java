import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.retry.RetryUtils;

public class CodeBuildClientRetryCondition implements RetryPolicy.RetryCondition{

    @Override
    public boolean shouldRetry(AmazonWebServiceRequest amazonWebServiceRequest, AmazonClientException e, int i) {
        return RetryUtils.isThrottlingException(e);
    }
}
