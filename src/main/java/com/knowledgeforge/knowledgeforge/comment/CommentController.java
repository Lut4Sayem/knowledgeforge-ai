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

    @GetMapping("/nodes/{nodeId}/comments")
    public ResponseEntity<List<CommentResponseDTO>> getComments(@PathVariable String nodeId) {
        return ResponseEntity.ok(commentService.getComments(nodeId));
    }

    @PostMapping("/nodes/{nodeId}/comments")
    public ResponseEntity<CommentResponseDTO> addComment(
            @PathVariable String nodeId,
            @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.status(201).body(commentService.addComment(nodeId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, String>> deleteMyComment(@PathVariable String commentId) {
        commentService.deleteMyComment(commentId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }
}