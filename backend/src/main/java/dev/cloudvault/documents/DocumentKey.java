package dev.cloudvault.documents;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
public class DocumentKey {
    @PrimaryKeyColumn(name = "owner_id", type = PrimaryKeyType.PARTITIONED)
    private String ownerId;

    @PrimaryKeyColumn(name = "document_id", type = PrimaryKeyType.CLUSTERED)
    private UUID documentId;

    protected DocumentKey() {}

    public DocumentKey(String ownerId, UUID documentId) {
        this.ownerId = ownerId;
        this.documentId = documentId;
    }

    public String getOwnerId() { return ownerId; }
    public UUID getDocumentId() { return documentId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DocumentKey that)) return false;
        return Objects.equals(ownerId, that.ownerId) && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() { return Objects.hash(ownerId, documentId); }
}
