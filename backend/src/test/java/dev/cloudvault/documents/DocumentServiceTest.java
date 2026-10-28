package dev.cloudvault.documents;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock DocumentRepository repository;
    @Mock ObjectStorage storage;

    private DocumentService service() {
        return new DocumentService(repository, storage, 1024);
    }

    @Test
    void createsAnUploadIntentInsideTheAuthenticatedOwnersNamespace() {
        var owner = "google-subject-42";
        var request = new DocumentService.CreateDocumentRequest("notes.txt", "text/plain", 12);
        when(repository.save(any(DocumentRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(storage.createUploadLink(any())).thenReturn(new ObjectStorage.UploadLink(
            "https://s3.example.test/", java.util.Map.of("key", "users/hash/uuid"),
            Instant.parse("2026-01-01T00:05:00Z")));

        var intent = service().createUpload(owner, request);

        assertThat(intent.uploadUrl()).isEqualTo("https://s3.example.test/");
        assertThat(intent.contentType()).isEqualTo("text/plain");
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(document ->
            document.getKey().getOwnerId().equals(owner)
                && document.getObjectKey().startsWith("users/")
                && document.getStatus() == DocumentStatus.PENDING_UPLOAD));
    }

    @Test
    void rejectsPathLikeNamesAndUnsupportedFileTypes() {
        assertThatThrownBy(() -> service().createUpload("owner", new DocumentService.CreateDocumentRequest(
            "../private.txt", "text/plain", 12)))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service().createUpload("owner", new DocumentService.CreateDocumentRequest(
            "script.html", "text/html", 12)))
            .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void looksUpEveryDocumentWithinTheCurrentOwnersPartition() {
        var requestedId = UUID.randomUUID();
        when(repository.findOwnedDocument("current-user", requestedId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createDownload("current-user", requestedId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");

        verify(repository).findOwnedDocument("current-user", requestedId);
        verify(storage, never()).createDownloadLink(any());
    }

    @Test
    void onlyCompletedUploadsCanBeDownloaded() {
        var id = UUID.randomUUID();
        var record = new DocumentRecord(new DocumentKey("owner", id), "draft.pdf", "application/pdf", 10,
            "users/hash/" + id, DocumentStatus.PENDING_UPLOAD, Instant.now());
        when(repository.findOwnedDocument("owner", id)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service().createDownload("owner", id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409 CONFLICT");
        verify(storage, never()).createDownloadLink(any());
    }

    @Test
    void listsOnlyTheOwnersPartition() {
        when(repository.findAllForOwner("owner-a")).thenReturn(List.of());

        assertThat(service().list("owner-a")).isEmpty();

        verify(repository).findAllForOwner("owner-a");
    }
}
