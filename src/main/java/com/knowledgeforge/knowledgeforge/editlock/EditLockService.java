package com.knowledgeforge.knowledgeforge.editlock;

import com.knowledgeforge.knowledgeforge.access.NodeAccessService;
import com.knowledgeforge.knowledgeforge.common.UserRef;
import com.knowledgeforge.knowledgeforge.user.User;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

/** Soft edit lock (Release 1a only, replaced by real-time editing in 1b). One document per node, _id = node id. */
@Service
public class EditLockService {
    static final long TTL_MS = 60_000;

    private final EditLockRepository locks;
    private final NodeAccessService access;
    private final MongoTemplate mongo;

    public EditLockService(EditLockRepository locks, NodeAccessService access, MongoTemplate mongo) {
        this.locks = locks;
        this.access = access;
        this.mongo = mongo;
    }

    /**
     * Free, expired or mine -> take/renew it. Held by someone else -> report the holder, unless force.
     * Atomic: one upsert whose filter only matches a free/expired/own lock; if another user's live lock
     * is there the upsert collides on _id (DuplicateKeyException) and we just read the holder.
     */
    public LockDTO acquire(String nodeId, boolean force) {
        access.requireEdit(nodeId);
        User user = access.currentUser();
        UserRef me = new UserRef(user.getId(), user.getFullName(), user.getProfilePicture());
        Date now = new Date();
        Date expires = new Date(now.getTime() + TTL_MS);

        Criteria free = Criteria.where("id").is(nodeId);
        if (!force) {
            free = free.orOperator(Criteria.where("expiresAt").lte(now), Criteria.where("holder.userId").is(user.getId()));
        }
        try {
            mongo.findAndModify(Query.query(free), new Update().set("holder", me).set("expiresAt", expires),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), EditLock.class);
            return new LockDTO(true, me, expires);
        } catch (DuplicateKeyException e) {
            EditLock current = locks.findById(nodeId).orElse(null);
            if (current == null) return acquire(nodeId, force); // it expired and was cleaned up between the two calls
            return new LockDTO(user.getId().equals(current.getHolder().getUserId()), current.getHolder(), current.getExpiresAt());
        }
    }

    /** Releases the lock if it is mine; does nothing otherwise. */
    public void release(String nodeId) {
        access.requireView(nodeId);
        String me = access.currentUser().getId();
        mongo.remove(Query.query(Criteria.where("id").is(nodeId).and("holder.userId").is(me)), EditLock.class);
    }
}
