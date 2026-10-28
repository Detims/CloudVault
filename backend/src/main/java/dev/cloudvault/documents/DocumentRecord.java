package dev.cloudvault.documents;

import java.time.Instant;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("documents")
public class DocumentRecord {
    @PrimaryKey
    private DocumentKey key;

    @Column("file_name")
    private String fileName;

    @Column("content_type")
    private String contentType;

    @Column("size_bytes")
    private long sizeBytes;

    @Column("object_key")
    private String objectKey;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    protected DocumentRecord() {}

    public DocumentRecord(DocumentKey key, String fileName, String contentType, long sizeBytes,
                          String objectKey, DocumentStatus status, Instant createdAt) {
        this.key = key;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.status = status.name();
        this.createdAt = createdAt;
    }

    public DocumentKey getKey() { return key; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getObjectKey() { return objectKey; }
    public DocumentStatus getStatus() { return DocumentStatus.valueOf(status); }
    public Instant getCreatedAt() { return createdAt; }

    public void markAvailable() { this.status = DocumentStatus.AVAILABLE.name(); }
}
