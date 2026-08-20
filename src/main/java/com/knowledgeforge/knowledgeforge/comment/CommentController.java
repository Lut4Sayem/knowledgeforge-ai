package com.knowledgeforge.knowledgeforge.comment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/documents/{documentId}/comments")
    public ResponseEntity<List<CommentResponseDTO>> getComments(@PathVariable String documentId) {
        return ResponseEntity.ok(commentService.getComments(documentId));
    }

    @PostMapping("/documents/{documentId}/comments")
    public ResponseEntity<CommentResponseDTO> addComment(
            @PathVariable String documentId,
            @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.status(201).body(commentService.addComment(documentId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, String>> deleteMyComment(@PathVariable String commentId) {
        commentService.deleteMyComment(commentId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}