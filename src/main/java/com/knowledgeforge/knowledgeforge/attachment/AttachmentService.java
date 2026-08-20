package com.knowledgeforge.knowledgeforge.attachment;

import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final DocumentService documentService;
    private final UserRepository userRepository;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            DocumentService documentService,
            UserRepository userRepository
    ) {
        this.attachmentRepository = attachmentRepository;
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    public List<Attachment> getAttachments(String documentId) {
        documentService.getDocumentByID(documentId);

        return attachmentRepository.findByDocumentId(documentId);
    }

    public Attachment addAttachment(String documentId, CreateAttachmentRequest request) {
        // membership check
        documentService.getDocumentByID(documentId);

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Attachment a = new Attachment();
        a.setDocumentId(documentId);
        a.setOriginalName(request.getOriginalName());
        a.setUrl(request.getUrl());
        a.setUploadedBy(user.getId());
        a.setUploadedAt(new Date());

        return attachmentRepository.save(a);
    }

    public void deleteAttachment(String attachmentId) {
        Attachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));

        documentService.getDocumentByID(a.getDocumentId());

        attachmentRepository.deleteById(attachmentId);
    }
}