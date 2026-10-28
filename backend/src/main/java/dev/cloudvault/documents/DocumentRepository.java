package dev.cloudvault.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface DocumentRepository extends CassandraRepository<DocumentRecord, DocumentKey> {
    @Query("SELECT * FROM documents WHERE owner_id = ?0")
    List<DocumentRecord> findAllForOwner(String ownerId);

    @Query("SELECT * FROM documents WHERE owner_id = ?0 AND document_id = ?1")
    Optional<DocumentRecord> findOwnedDocument(String ownerId, UUID documentId);
}
