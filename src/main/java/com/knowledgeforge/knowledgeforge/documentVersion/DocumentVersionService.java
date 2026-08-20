package com.knowledgeforge.knowledgeforge.documentVersion;

import com.knowledgeforge.knowledgeforge.documentPkg.DocumentEntity;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentRepository;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class DocumentVersionService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService; // membership check
    private final UserRepository userRepository;

    public DocumentVersionService(
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            DocumentService documentService,
            UserRepository userRepository
    ) {
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    public List<DocumentVersionResponseDTO> getVersions(String documentId) {
        // membership check
        documentService.getDocumentByID(documentId);

        List<DocumentVersion> versions =
                documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);

        List<DocumentVersionResponseDTO> out = new ArrayList<>();
        for (DocumentVersion v : versions) {
            DocumentVersionResponseDTO dto = new DocumentVersionResponseDTO();
            dto.setId(v.getId());
            dto.setVersionNumber(v.getVersionNumber());
            dto.setSavedAt(v.getSavedAt());
            dto.setTitle(v.getTitle());
            dto.setContent(v.getContent());
            dto.setSavedByUserId(v.getSavedBy());

            userRepository.findById(v.getSavedBy()).ifPresent(u -> {
                dto.setSavedByFullName(u.getFullName());
                dto.setSavedByEmail(u.getEmail());
            });

            out.add(dto);
        }
        return out;
    }

    public void restoreVersion(String documentId, String versionId) {
        // membership check
        DocumentEntity doc = documentService.getDocumentByID(documentId);

        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version not found"));

        if (!documentId.equals(version.getDocumentId())) {
            throw new RuntimeException("Version does not belong to this document");
        }

        // who is restoring
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        saveVersionSnapshot(doc, user.getId());

        // restore title/content
        doc.setTitle(version.getTitle());
        doc.setContent(version.getContent());
        doc.setUpdatedBy(user.getId());
        doc.setUpdatedAt(new Date());

        documentRepository.save(doc);
    }

    private void saveVersionSnapshot(DocumentEntity doc, String userId) {
        int nextNumber = 1;
        var lastOpt = documentVersionRepository.findTopByDocumentIdOrderByVersionNumberDesc(doc.getId());
        if (lastOpt.isPresent() && lastOpt.get().getVersionNumber() != null) {
            nextNumber = lastOpt.get().getVersionNumber() + 1;
        }

        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(doc.getId());
        v.setTitle(doc.getTitle());
        v.setContent(doc.getContent());
        v.setVersionNumber(nextNumber);
        v.setSavedBy(userId);
        v.setSavedAt(new Date());

        documentVersionRepository.save(v);
    }
}