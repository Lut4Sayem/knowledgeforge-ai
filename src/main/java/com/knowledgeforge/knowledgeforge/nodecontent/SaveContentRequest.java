package com.knowledgeforge.knowledgeforge.nodecontent;

/** state is the base64 Yjs update; baseRevision is the revision the client last saw. */
public record SaveContentRequest(Long baseRevision, String state, String text) {
}
