package dev.cloudvault.documents;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png", "text/plain");

    private final DocumentRepository repository;
    private final ObjectStorage storage;
    private final long maxFileBytes;

    public DocumentService(DocumentRepository repository, ObjectStorage storage,
                           @Value("${cloudvault.storage.s3.max-file-bytes}") long maxFileBytes) {
        this.repository = repository;
        this.storage = storage;
        this.maxFileBytes = maxFileBytes;
    }

    public List<DocumentView> list(String ownerId) {
        return repository.findAllForOwner(ownerId).stream().map(DocumentView::from).toList();
    }

    public UploadIntent createUpload(String ownerId, CreateDocumentRequest request) {
        var name = request.fileName().trim();
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name must not contain a path");
        }
        if (!ALLOWED_TYPES.contains(request.contentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type is not allowed");
        }
        if (request.sizeBytes() < 1 || request.sizeBytes() > maxFileBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File size is outside the allowed range");
        }

        var documentId = UUID.randomUUID();
        var document = new DocumentRecord(
            new DocumentKey(ownerId, documentId), name, request.contentType(), request.sizeBytes(),
            objectKey(ownerId, documentId), DocumentStatus.PENDING_UPLOAD, Instant.now());
        var link = storage.createUploadLink(document);
        repository.save(document);
        return new UploadIntent(documentId, link.url(), link.fields(), link.expiresAt(),
            document.getContentType(), maxFileBytes);
    }

    public DocumentView completeUpload(String ownerId, UUID documentId) {
        var document = findOwned(ownerId, documentId);
        var actualSize = storage.uploadedSize(document);
        if (actualSize != document.getSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file size does not match the upload intent");
        }
        document.markAvailable();
        return DocumentView.from(repository.save(document));
    }

    public DownloadIntent createDownload(String ownerId, UUID documentId) {
        var document = findOwned(ownerId, documentId);
        requireAvailable(document);
        var link = storage.createDownloadLink(document);
        return new DownloadIntent(link.url(), link.expiresAt());
    }

    public void delete(String ownerId, UUID documentId) {
        var document = findOwned(ownerId, documentId);
        storage.delete(document);
        repository.delete(document);
    }

    private DocumentRecord findOwned(String ownerId, UUID documentId) {
        return repository.findOwnedDocument(ownerId, documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void requireAvailable(DocumentRecord document) {
        if (document.getStatus() != DocumentStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload has not been completed");
        }
    }

    private String objectKey(String ownerId, UUID documentId) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(ownerId.getBytes(StandardCharsets.UTF_8));
            return "users/" + HexFormat.of().formatHex(digest) + "/" + documentId;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    public record CreateDocumentRequest(String fileName, String contentType, long sizeBytes) {}
    public record UploadIntent(UUID documentId, String uploadUrl, java.util.Map<String, String> fields,
                               Instant expiresAt, String contentType, long maxFileBytes) {}
    public record DownloadIntent(String downloadUrl, Instant expiresAt) {}
    public record DocumentView(UUID id, String fileName, String contentType, long sizeBytes,
                               String status, Instant createdAt) {
        static DocumentView from(DocumentRecord document) {
            return new DocumentView(document.getKey().getDocumentId(), document.getFileName(),
                document.getContentType(), document.getSizeBytes(), document.getStatus().name(),
                document.getCreatedAt());
        }
    }
}
