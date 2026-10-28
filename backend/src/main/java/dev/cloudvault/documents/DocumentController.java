package dev.cloudvault.documents;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    List<DocumentService.DocumentView> list(@AuthenticationPrincipal OidcUser user) {
        return documents.list(user.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocumentService.UploadIntent create(@AuthenticationPrincipal OidcUser user,
                                        @Valid @RequestBody CreateDocumentBody request) {
        return documents.createUpload(user.getSubject(),
            new DocumentService.CreateDocumentRequest(request.fileName(), request.contentType(), request.sizeBytes()));
    }

    @PostMapping("/{id}/complete")
    DocumentService.DocumentView complete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return documents.completeUpload(user.getSubject(), id);
    }

    @GetMapping("/{id}/download")
    DocumentService.DownloadIntent download(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return documents.createDownload(user.getSubject(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        documents.delete(user.getSubject(), id);
    }

    record CreateDocumentBody(
        @NotBlank @Size(max = 180) String fileName,
        @NotBlank String contentType,
        @Positive long sizeBytes) {}
}
