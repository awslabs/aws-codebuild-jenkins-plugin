import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

public class CodeBuildException extends InterruptedException{
    private final CodeBuildResult codeBuildResult;

    public CodeBuildException(CodeBuildResult codeBuildResult) {
        this.codeBuildResult = codeBuildResult;
    }

    @Whitelisted
    public CodeBuildResult getCodeBuildResult() {
        return codeBuildResult;
    }
}
