package com.knowledgeforge.knowledgeforge.user;

import com.knowledgeforge.knowledgeforge.attachment.AttachmentStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Uploaded profile pictures, stored in GridFS next to attachments.
 * shortcut: pictures don't count toward the 300 MB attachment cap; one 2 MB file per user. Count them if users grow.
 */
@Service
public class ProfilePictureService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Raster images only (no SVG: it can carry script). The served type always comes from here. */
    static final Map<String, String> ALLOWED_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp"
    );

    private final UserRepository userRepository;
    private final AttachmentStorageService storage;

    public ProfilePictureService(UserRepository userRepository, AttachmentStorageService storage) {
        this.userRepository = userRepository;
        this.storage = storage;
    }

    public record Picture(String contentType, Resource resource) {}

    public User upload(MultipartFile file) {
        User user = currentUser();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Picture is too large (max 2 MB)");
        }
        String contentType = ALLOWED_TYPES.get(extensionOf(file.getOriginalFilename()));
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a PNG, JPG, GIF or WebP image");
        }

        String fileId;
        try (InputStream in = file.getInputStream()) {
            fileId = storage.store(in, contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the uploaded file");
        }

        String oldFileId = user.getPictureFileId();
        user.setPictureFileId(fileId);
        user.setPictureContentType(contentType);
        // The file id in the URL changes on every upload, so browsers can cache each version forever
        user.setProfilePicture("/api/users/" + user.getId() + "/picture?v=" + fileId);
        user.setUpdatedAt(new Date());
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (RuntimeException e) {
            storage.delete(fileId); // no orphan file if the user can't be saved
            throw e;
        }
        storage.delete(oldFileId);
        return saved;
    }

    /** Clears the picture (uploaded or an old link) and deletes the stored file. */
    public User remove() {
        User user = currentUser();
        String oldFileId = user.getPictureFileId();
        user.setPictureFileId(null);
        user.setPictureContentType(null);
        user.setProfilePicture(null);
        user.setUpdatedAt(new Date());
        User saved = userRepository.save(user);
        storage.delete(oldFileId);
        return saved;
    }

    /** Any logged-in user may see any profile picture (teammates see avatars on documents and comments). */
    public Picture load(String userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getPictureFileId() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No picture"));
        return new Picture(user.getPictureContentType(), storage.load(user.getPictureFileId()));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
