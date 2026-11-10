package com.knowledgeforge.knowledgeforge.editlock;

import com.knowledgeforge.knowledgeforge.node.NodeDTO;
import com.knowledgeforge.knowledgeforge.support.NodeIntegrationTest;
import com.knowledgeforge.knowledgeforge.user.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class EditLockServiceIT extends NodeIntegrationTest {
    @Autowired EditLockService locks;
    @Autowired EditLockRepository lockRepo;

    @Test
    void acquireFreeLock() {
        NodeDTO p = createPage("A");
        LockDTO l = locks.acquire(p.id(), false);
        assertTrue(l.mine());
        assertEquals(admin.getId(), l.holder().getUserId());
        long secs = (l.expiresAt().getTime() - System.currentTimeMillis()) / 1000;
        assertTrue(secs > 50 && secs <= 60, "expires in about 60 s, was " + secs);
    }

    @Test
    void renewingOwnLockExtendsIt() {
        NodeDTO p = createPage("A");
        locks.acquire(p.id(), false);
        expireIn(p.id(), 5);
        LockDTO l = locks.acquire(p.id(), false);
        assertTrue(l.mine());
        assertTrue(l.expiresAt().getTime() - System.currentTimeMillis() > 50_000);
    }

    @Test
    void secondUserSeesHolder() {
        NodeDTO p = createPage("A");
        locks.acquire(p.id(), false);
        User other = newMember("EDITOR");
        loginAs(other);
        LockDTO l = locks.acquire(p.id(), false);
        assertFalse(l.mine());
        assertEquals(admin.getId(), l.holder().getUserId());
        loginAs(admin);
        assertEquals(admin.getId(), lockRepo.findById(p.id()).orElseThrow().getHolder().getUserId());
    }

    @Test
    void expiredLockCanBeTaken() {
        NodeDTO p = createPage("A");
        locks.acquire(p.id(), false);
        expireIn(p.id(), -1);
        User other = newMember("EDITOR");
        loginAs(other);
        LockDTO l = locks.acquire(p.id(), false);
        assertTrue(l.mine());
        assertEquals(other.getId(), l.holder().getUserId());
    }

    @Test
    void forceTakesOver() {
        NodeDTO p = createPage("A");
        locks.acquire(p.id(), false);
        User other = newMember("EDITOR");
        loginAs(other);
        LockDTO l = locks.acquire(p.id(), true);
        assertTrue(l.mine());
        assertEquals(other.getId(), lockRepo.findById(p.id()).orElseThrow().getHolder().getUserId());
    }

    @Test
    void releaseOnlyOwn() {
        NodeDTO p = createPage("A");
        locks.acquire(p.id(), false);
        loginAs(newMember("EDITOR"));
        locks.release(p.id());
        assertTrue(lockRepo.existsById(p.id()), "someone else's release does nothing");
        loginAs(admin);
        locks.release(p.id());
        assertFalse(lockRepo.existsById(p.id()));
        locks.release(p.id()); // releasing again is fine
    }

    @Test
    void viewerCannotLock() {
        NodeDTO p = createPage("A");
        loginAs(newMember("VIEWER"));
        assertEquals(403, statusOf(() -> locks.acquire(p.id(), false)));
    }

    @Test
    void twoUsersAcquiringAtOnceOnlyOneWins() throws Exception {
        NodeDTO p = createPage("A");
        User other = newMember("EDITOR");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var go = new java.util.concurrent.CyclicBarrier(2);
        var results = pool.invokeAll(java.util.List.<java.util.concurrent.Callable<Boolean>>of(
                () -> { loginAs(admin); go.await(); return locks.acquire(p.id(), false).mine(); },
                () -> { loginAs(other); go.await(); return locks.acquire(p.id(), false).mine(); }));
        pool.shutdown();
        assertNotEquals(results.get(0).get(), results.get(1).get());
    }

    private void expireIn(String nodeId, int seconds) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(nodeId)),
                new Update().set("expiresAt", new Date(System.currentTimeMillis() + seconds * 1000L)), EditLock.class);
    }
}
