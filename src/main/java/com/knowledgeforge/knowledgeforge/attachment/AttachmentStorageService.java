package com.knowledgeforge.knowledgeforge.attachment;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * The only class that talks to GridFS. Swap this for S3/R2 later without touching callers.
 */
@Service
public class AttachmentStorageService {

    private final GridFsOperations gridFs;

    public AttachmentStorageService(GridFsOperations gridFs) {
        this.gridFs = gridFs;
    }

    /** Stores the bytes and returns the new file id. The user's filename is never used for storage. */
    public String store(InputStream content, String contentType) {
        ObjectId id = gridFs.store(content, "attachment", contentType);
        return id.toHexString();
    }

    public Resource load(String fileId) {
        GridFSFile file = ObjectId.isValid(fileId) ? gridFs.findOne(byId(fileId)) : null;
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }
        return gridFs.getResource(file);
    }

    public void delete(String fileId) {
        if (fileId != null && ObjectId.isValid(fileId)) {
            gridFs.delete(byId(fileId));
        }
    }

    private static Query byId(String fileId) {
        return new Query(where("_id").is(new ObjectId(fileId)));
    }
}
