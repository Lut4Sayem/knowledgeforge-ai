package com.knowledgeforge.knowledgeforge.comment;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
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
    private final NodeAccessService access;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, NodeAccessService access, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.access = access;
        this.userRepository = userRepository;
    }

    public List<CommentResponseDTO> getComments(String nodeId) {
        access.requireView(nodeId);

        String me = currentUserId();
        List<Comment> comments = commentRepository.findByNodeIdOrderByCreatedAtAsc(nodeId).stream()
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

    public CommentResponseDTO addComment(String nodeId, CreateCommentRequest request) {
        access.requireView(nodeId); // VIEWERs may comment

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment cannot be empty");
        }

        Comment c = new Comment();
        c.setNodeId(nodeId);
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

        access.requireView(c.getNodeId());

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.getId().equals(c.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comment");
        }

        commentRepository.deleteById(commentId);
    }
}