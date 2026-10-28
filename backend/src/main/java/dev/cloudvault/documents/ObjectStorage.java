package dev.cloudvault.documents;

import java.time.Instant;
import java.util.Map;

public interface ObjectStorage {
    UploadLink createUploadLink(DocumentRecord document);
    DownloadLink createDownloadLink(DocumentRecord document);
    long uploadedSize(DocumentRecord document);
    void delete(DocumentRecord document);

    record UploadLink(String url, Map<String, String> fields, Instant expiresAt) {}
    record DownloadLink(String url, Instant expiresAt) {}
}
