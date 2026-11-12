package com.knowledgeforge.knowledgeforge.attachment;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.documentPkg.DocumentService;
import com.knowledgeforge.knowledgeforge.user.User;
import com.knowledgeforge.knowledgeforge.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AttachmentService {

    /** Same as spring.servlet.multipart.max-file-size; checked here too so the rule is testable. */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    /** Atlas free tier is 512 MB for everything, and a full DB blocks all writes. */
    static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;
    static final int MAX_NAME_LENGTH = 200;

    /** Extension allowlist. The content type always comes from here, never from the client. */
    static final Map<String, String> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    );

    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService storage;
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final NodeAccessService access;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            AttachmentStorageService storage,
            DocumentService documentService,
            UserRepository userRepository,
            NodeAccessService access
    ) {
        this.access = access;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.documentService = documentService;
        this.userRepository = userRepository;
    }

    /** A stored file plus the metadata the controller needs for safe download headers. */
    public record FileDownload(Attachment attachment, Resource content) {}

    public List<Attachment> getAttachments(String documentId) {
        documentService.getDocumentByID(documentId);

        return attachmentRepository.findByDocumentId(documentId);
    }

    public Attachment addAttachment(String documentId, CreateAttachmentRequest request) {
        documentService.getDocumentForEdit(documentId);

        Attachment a = new Attachment();
        a.setDocumentId(documentId);
        a.setOriginalName(request.getOriginalName());
        a.setUrl(request.getUrl());
        a.setUploadedBy(currentUser().getId());
        a.setUploadedAt(new Date());

        return attachmentRepository.save(a);
    }

    public List<Attachment> getNodeAttachments(String nodeId) {
        access.requireView(nodeId);
        return attachmentRepository.findByNodeId(nodeId);
    }

    public Attachment uploadNodeFile(String nodeId, MultipartFile file) {
        access.requireEdit(nodeId);
        return store(file, a -> a.setNodeId(nodeId));
    }

    public Attachment uploadFile(String documentId, MultipartFile file) {
        documentService.getDocumentForEdit(documentId);
        return store(file, a -> a.setDocumentId(documentId));
    }

    private Attachment store(MultipartFile file, java.util.function.Consumer<Attachment> owner) {
        User user = currentUser();

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (max 5 MB)");
        }

        String name = cleanFileName(file.getOriginalFilename());
        String contentType = ALLOWED_TYPES.get(extensionOf(name));
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File type not allowed. Allowed: pdf, png, jpg, jpeg, gif, webp, txt, md, csv, docx, xlsx, pptx");
        }

        // shortcut: two parallel uploads can both pass this check; the ~200 MB headroom to 512 MB covers that.
        if (totalStoredBytes() + file.getSize() > MAX_TOTAL_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage limit reached. Delete some files first.");
        }

        String fileId;
        try (InputStream in = file.getInputStream()) {
            fileId = storage.store(in, contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the uploaded file");
        }

        Attachment a = new Attachment();
        owner.accept(a);
        a.setOriginalName(name);
        a.setFileId(fileId);
        a.setContentType(contentType);
        a.setSize(file.getSize());
        a.setUploadedBy(user.getId());
        a.setUploadedAt(new Date());
        try {
            return attachmentRepository.save(a);
        } catch (RuntimeException e) {
            storage.delete(fileId); // don't leave an orphan file if the record can't be saved
            throw e;
        }
    }

    public FileDownload download(String attachmentId) {
        Attachment a = findAttachment(attachmentId);
        requireView(a); // anyone who can see the page may download

        if (a.getFileId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This attachment is a link, not a file");
        }
        return new FileDownload(a, storage.load(a.getFileId()));
    }

    public void deleteAttachment(String attachmentId) {
        Attachment a = findAttachment(attachmentId);
        requireEdit(a);

        // Record first: if the file delete then fails we only leak storage, never show a broken attachment.
        attachmentRepository.deleteById(attachmentId);
        storage.delete(a.getFileId());
    }

    private void requireView(Attachment a) {
        if (a.getNodeId() != null) access.requireView(a.getNodeId());
        else documentService.getDocumentByID(a.getDocumentId());
    }

    private void requireEdit(Attachment a) {
        if (a.getNodeId() != null) access.requireEdit(a.getNodeId());
        else documentService.getDocumentForEdit(a.getDocumentId());
    }

    private Attachment findAttachment(String attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private long totalStoredBytes() {
        return attachmentRepository.findByFileIdIsNotNull().stream()
                .mapToLong(x -> x.getSize() == null ? 0 : x.getSize())
                .sum();
    }

    /** Display name only: strip path separators and control chars, cap length but keep the extension. */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original.replaceAll("[/\\\\\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name is missing");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String ext = extensionOf(name);
            String tail = ext.isEmpty() || ext.length() >= MAX_NAME_LENGTH / 2 ? "" : "." + ext;
            name = name.substring(0, MAX_NAME_LENGTH - tail.length()) + tail;
        }
        return name;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
