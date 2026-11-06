package com.knowledgeforge.knowledgeforge.documentPkg;

import com.knowledgeforge.knowledgeforge.activity.ActivityLogService;
import com.knowledgeforge.knowledgeforge.ai.AiSummaryRepository;
import com.knowledgeforge.knowledgeforge.attachment.Attachment;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentRepository;
import com.knowledgeforge.knowledgeforge.attachment.AttachmentStorageService;
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
    final ActivityLogService activityLogService;
    final AttachmentRepository attachmentRepository;
    final AttachmentStorageService attachmentStorage;
    final AiSummaryRepository aiSummaryRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            SpaceRepository spaceRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            DocumentVersionRepository documentVersionRepository,
            ActivityLogService activityLogService,
            AttachmentRepository attachmentRepository,
            AttachmentStorageService attachmentStorage,
            AiSummaryRepository aiSummaryRepository
    ) {
        this.documentRepository = documentRepository;
        this.spaceRepository = spaceRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.activityLogService = activityLogService;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorage = attachmentStorage;
        this.aiSummaryRepository = aiSummaryRepository;
    }

    public DocumentEntity createDocument(String spaceId, CreateDocumentRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));

        if (!teamMemberRepository.canEdit(space.getTeamId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an ADMIN or EDITOR can create documents");
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

        DocumentEntity saved = documentRepository.save(documentEntity);

        activityLogService.log(
                space.getTeamId(),
                user.getId(),
                "CREATE_DOCUMENT",
                "DOCUMENT",
                saved.getId(),
                saved.getTitle()
        );

        return saved;
    }

    public List<DocumentEntity> getDocuments(String spaceId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));

        boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(space.getTeamId(), user.getId());
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        return documentRepository.findBySpaceId(spaceId);
    }

    public DocumentEntity getDocumentByID(String documentId) {
        return loadDocument(documentId, false);
    }

    // Same as getDocumentByID, but also requires ADMIN or EDITOR.
    public DocumentEntity getDocumentForEdit(String documentId) {
        return loadDocument(documentId, true);
    }

    private DocumentEntity loadDocument(String documentId, boolean forEdit) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Space space = spaceRepository.findById(document.getSpaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));

        boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(space.getTeamId(), user.getId());
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this team");
        }
        if (forEdit && !teamMemberRepository.canEdit(space.getTeamId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an ADMIN or EDITOR can change documents");
        }

        return document;
    }

    public DocumentEntity updateDocument(String documentId, UpdateDocumentRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        DocumentEntity document = getDocumentForEdit(documentId);

        // Save OLD state before changing it
        saveVersionSnapshot(document, user.getId());

        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setStatus(request.getStatus());
        document.setTags(request.getTags());
        document.setUpdatedBy(user.getId());
        document.setUpdatedAt(new Date());

        DocumentEntity saved = documentRepository.save(document);

        Space space = spaceRepository.findById(saved.getSpaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));

        activityLogService.log(
                space.getTeamId(),
                user.getId(),
                "UPDATE_DOCUMENT",
                "DOCUMENT",
                saved.getId(),
                saved.getTitle()
        );

        return saved;
    }

    public void deleteDocument(String documentId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        DocumentEntity document = getDocumentForEdit(documentId);

        Space space = spaceRepository.findById(document.getSpaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Space not found"));

        activityLogService.log(
                space.getTeamId(),
                user.getId(),
                "DELETE_DOCUMENT",
                "DOCUMENT",
                document.getId(),
                document.getTitle()
        );

        // Remove its attachments and their stored files, so nothing is left orphaned
        List<Attachment> attachments = attachmentRepository.findByDocumentId(documentId);
        attachmentRepository.deleteAll(attachments);
        attachments.forEach(a -> attachmentStorage.delete(a.getFileId()));

        aiSummaryRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
    }

    private void saveVersionSnapshot(DocumentEntity doc, String userId) {
        int nextNumber = 1;

        var lastOpt = documentVersionRepository
                .findTopByDocumentIdOrderByVersionNumberDesc(doc.getId());

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