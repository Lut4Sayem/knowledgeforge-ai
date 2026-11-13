package com.knowledgeforge.knowledgeforge.comment;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final DocumentService documentService; // reuse membership check
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, DocumentService documentService, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    public List<CommentResponseDTO> getComments(String documentId) {
        // membership check (403 if not member)
        documentService.getDocumentByID(documentId);

        String me = currentUserId();
        List<Comment> comments = commentRepository.findByDocumentIdOrderByCreatedAtAsc(documentId).stream()
                .filter(c -> c.getPrivateTo() == null || c.getPrivateTo().equals(me)).toList();

        List<CommentResponseDTO> out = new ArrayList<>();
        for (Comment c : comments) {
            CommentResponseDTO dto = new CommentResponseDTO();
            dto.setId(c.getId());
            dto.setContent(c.getContent());
            dto.setCreatedAt(c.getCreatedAt());
            dto.setCreatedByUserId(c.getCreatedBy());

            // manual join: user info
            userRepository.findById(c.getCreatedBy()).ifPresent(u -> {
                dto.setCreatedByFullName(u.getFullName());
                dto.setCreatedByEmail(u.getEmail());
            });

            out.add(dto);
        }

        return out;
    }

    private String currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")).getId();
    }

    public CommentResponseDTO addComment(String documentId, CreateCommentRequest request) {
        // membership check
        documentService.getDocumentByID(documentId);

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment cannot be empty");
        }

        Comment c = new Comment();
        c.setDocumentId(documentId);
        c.setContent(request.getContent().trim());
        c.setCreatedBy(user.getId());
        c.setCreatedAt(new Date());

        Comment saved = commentRepository.save(c);

        CommentResponseDTO dto = new CommentResponseDTO();
        dto.setId(saved.getId());
        dto.setContent(saved.getContent());
        dto.setCreatedAt(saved.getCreatedAt());
        dto.setCreatedByUserId(user.getId());
        dto.setCreatedByFullName(user.getFullName());
        dto.setCreatedByEmail(user.getEmail());
        return dto;
    }

    public void deleteMyComment(String commentId) {
        Comment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));

        // membership check based on comment's document
        documentService.getDocumentByID(c.getDocumentId());

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.getId().equals(c.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comment");
        }

        commentRepository.deleteById(commentId);
    }
}