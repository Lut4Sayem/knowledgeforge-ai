package com.knowledgeforge.knowledgeforge.documentVersion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;

    public DocumentVersionController(DocumentVersionService documentVersionService) {
        this.documentVersionService = documentVersionService;
    }

    @GetMapping("/documents/{documentId}/versions")
    public ResponseEntity<List<DocumentVersionResponseDTO>> getVersions(@PathVariable String documentId) {
        return ResponseEntity.ok(documentVersionService.getVersions(documentId));
    }

    @PostMapping("/documents/{documentId}/versions/{versionId}/restore")
    public ResponseEntity<Map<String, String>> restore(
            @PathVariable String documentId,
            @PathVariable String versionId
    ) {
        documentVersionService.restoreVersion(documentId, versionId);
        return ResponseEntity.ok(Map.of("message", "Restored"));
    }
}