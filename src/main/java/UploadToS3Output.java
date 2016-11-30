import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UploadToS3Output {

    @Getter private final String sourceLocation;
    @Getter private final String objectVersionId;
}
