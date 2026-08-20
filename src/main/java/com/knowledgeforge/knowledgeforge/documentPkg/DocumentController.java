package com.knowledgeforge.knowledgeforge.documentPkg;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/api/spaces/{spaceId}/documents")
    public ResponseEntity<DocumentEntity> createDocument(@PathVariable String spaceId, @RequestBody CreateDocumentRequest createDocumentRequest) {
        DocumentEntity body = documentService.createDocument(spaceId, createDocumentRequest);
        return ResponseEntity.status(201).body(body);
    }

    @GetMapping("/api/spaces/{spaceId}/documents")
    public List<DocumentEntity> getDocuments(@PathVariable String spaceId) {
        return documentService.getDocuments(spaceId);
    }

    @GetMapping("/api/documents/{documentId}")
    public ResponseEntity<DocumentEntity> getDocument(@PathVariable String documentId) {
        DocumentEntity document= documentService.getDocumentByID(documentId);
        return ResponseEntity.status(200).body(document);
    }
    @PutMapping("/api/documents/{documentId}")
    public ResponseEntity<DocumentEntity> updateDocument(@PathVariable String documentId, @RequestBody UpdateDocumentRequest updateDocumentRequest) {
        DocumentEntity body = documentService.updateDocument(documentId, updateDocumentRequest);
        return ResponseEntity.status(200).body(body);
    }

    @DeleteMapping("/api/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
