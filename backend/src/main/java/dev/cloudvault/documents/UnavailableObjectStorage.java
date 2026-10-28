package dev.cloudvault.documents;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
@ConditionalOnExpression("'${cloudvault.storage.s3.bucket:}' == ''")
class UnavailableObjectStorage implements ObjectStorage {
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Configure S3_BUCKET and AWS credentials to use document storage");
    }

    @Override public UploadLink createUploadLink(DocumentRecord document) { throw unavailable(); }
    @Override public DownloadLink createDownloadLink(DocumentRecord document) { throw unavailable(); }
    @Override public long uploadedSize(DocumentRecord document) { throw unavailable(); }
    @Override public void delete(DocumentRecord document) { throw unavailable(); }
}
