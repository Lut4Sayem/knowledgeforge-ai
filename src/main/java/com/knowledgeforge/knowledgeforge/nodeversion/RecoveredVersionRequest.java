package com.knowledgeforge.knowledgeforge.nodeversion;

/** state is the base64 Yjs update of the copy to keep; baseEpoch is the content epoch it was based on. */
public record RecoveredVersionRequest(String state, String text, Integer baseEpoch) {
}
