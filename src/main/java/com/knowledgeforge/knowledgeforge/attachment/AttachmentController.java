package com.knowledgeforge.knowledgeforge.attachment;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/nodes/{nodeId}/attachments")
    public ResponseEntity<List<Attachment>> getNodeAttachments(@PathVariable String nodeId) {
        return ResponseEntity.ok(attachmentService.getNodeAttachments(nodeId));
    }

    @PostMapping(value = "/nodes/{nodeId}/attachments/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Attachment> uploadNodeFile(@PathVariable String nodeId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(attachmentService.uploadNodeFile(nodeId, file));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable String attachmentId) {
        AttachmentService.FileDownload file = attachmentService.download(attachmentId);
        Attachment a = file.attachment();

        // Always a download (never rendered inline) + nosniff, so uploaded content can't run on our origin.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .contentLength(a.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(a.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Map<String, String>> deleteAttachment(@PathVariable String attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}
