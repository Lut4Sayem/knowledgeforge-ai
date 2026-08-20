package com.knowledgeforge.knowledgeforge.attachment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/documents/{documentId}/attachments")
    public ResponseEntity<List<Attachment>> getAttachments(@PathVariable String documentId) {
        return ResponseEntity.ok(attachmentService.getAttachments(documentId));
    }

    @PostMapping("/documents/{documentId}/attachments")
    public ResponseEntity<Attachment> addAttachment(
            @PathVariable String documentId,
            @RequestBody CreateAttachmentRequest request
    ) {
        Attachment saved = attachmentService.addAttachment(documentId, request);
        return ResponseEntity.status(201).body(saved);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Map<String, String>> deleteAttachment(@PathVariable String attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}