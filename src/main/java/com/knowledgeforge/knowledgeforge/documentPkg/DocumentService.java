package com.knowledgeforge.knowledgeforge.documentPkg;

import com.knowledgeforge.knowledgeforge.space.Space;
import com.knowledgeforge.knowledgeforge.space.SpaceRepository;
import com.knowledgeforge.knowledgeforge.team.TeamMemberRepository;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {
    final DocumentRepository documentRepository;
    final TeamMemberRepository teamMemberRepository;
    final UserRepository userRepository;
    final SpaceRepository spaceRepository;
    public DocumentService(DocumentRepository documentRepository, SpaceRepository spaceRepository, TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.spaceRepository = spaceRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    public DocumentEntity createDocument(String spaceId, CreateDocumentRequest request){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String userId = user.getId();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new RuntimeException("Space not found"));
        String teamId= space.getTeamId();
        boolean exits = teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
        if(!exits){
            throw new RuntimeException("User or Team not found");
        }
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setSpaceId(spaceId);
        documentEntity.setTitle(request.getTitle());
        documentEntity.setContent(request.getContent());
        documentEntity.setStatus(request.getStatus());
        documentEntity.setTags(request.getTags());
        documentEntity.setCreatedBy(userId);
        documentEntity.setUpdatedBy(userId);
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


}
