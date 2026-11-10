package com.knowledgeforge.knowledgeforge.nodecontent;

/** state is base64. A client may merge its copy into this state only if it was based on the same contentEpoch. */
public record ContentDTO(String format, String state, long revision, int contentEpoch) {
}
