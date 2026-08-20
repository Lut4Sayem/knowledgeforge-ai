package com.knowledgeforge.knowledgeforge.documentPkg;

import com.knowledgeforge.knowledgeforge.documentVersion.DocumentVersion;
import com.knowledgeforge.knowledgeforge.documentVersion.DocumentVersionRepository;
import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Service
public class DocumentService {

    final DocumentRepository documentRepository;
    final TeamMemberRepository teamMemberRepository;
    final UserRepository userRepository;
    final SpaceRepository spaceRepository;
    final DocumentVersionRepository documentVersionRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            SpaceRepository spaceRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            DocumentVersionRepository documentVersionRepository
    ) {
        this.documentRepository = documentRepository;
        this.spaceRepository = spaceRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    public DocumentEntity createDocument(String spaceId, CreateDocumentRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new RuntimeException("Space not found"));

        boolean exists = teamMemberRepository.existsByTeamIdAndUserId(space.getTeamId(), user.getId());
        if (!exists) {
            throw new RuntimeException("Forbidden");
        }

        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setSpaceId(spaceId);
        documentEntity.setTitle(request.getTitle());
        documentEntity.setContent(request.getContent());
        documentEntity.setStatus(request.getStatus());
        documentEntity.setTags(request.getTags());
        documentEntity.setCreatedBy(user.getId());
        documentEntity.setUpdatedBy(user.getId());
        documentEntity.setCreatedAt(new Date());
        documentEntity.setUpdatedAt(new Date());

        return documentRepository.save(documentEntity);
    }

    public List<DocumentEntity> getDocuments(String spaceId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new RuntimeException("Space not found"));

        boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(space.getTeamId(), user.getId());
        if (!isMember) {
            throw new RuntimeException("Forbidden");
        }

        return documentRepository.findBySpaceId(spaceId);
    }

    public DocumentEntity getDocumentByID(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Space space = spaceRepository.findById(document.getSpaceId())
                .orElseThrow(() -> new RuntimeException("Space not found"));

        boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(space.getTeamId(), user.getId());
        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this team"
            );
        }

        return document;
    }

    public DocumentEntity updateDocument(String documentId, UpdateDocumentRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        DocumentEntity document = getDocumentByID(documentId);
        saveVersionSnapshot(document, user.getId());
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setStatus(request.getStatus());
        document.setTags(request.getTags());
        document.setUpdatedBy(user.getId());
        document.setUpdatedAt(new Date());
        return documentRepository.save(document);
    }

    public void deleteDocument(String documentId) {
        getDocumentByID(documentId);
        documentRepository.deleteById(documentId);
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