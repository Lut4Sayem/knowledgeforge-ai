package com.knowledgeforge.knowledgeforge.editlock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class EditLockController {
    /** force is optional; the body may be empty. */
    public record AcquireRequest(boolean force) {}

    private final EditLockService service;

    public EditLockController(EditLockService service) {
        this.service = service;
    }

    @PostMapping("/api/nodes/{id}/edit-lock")
    public LockDTO acquire(@PathVariable String id, @RequestBody(required = false) AcquireRequest request) {
        return service.acquire(id, request != null && request.force());
    }

    @DeleteMapping("/api/nodes/{id}/edit-lock")
    public ResponseEntity<Void> release(@PathVariable String id) {
        service.release(id);
        return ResponseEntity.noContent().build();
    }
}
