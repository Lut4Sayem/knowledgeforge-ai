package com.knowledgeforge.knowledgeforge.node;

/** {@code movedToTop}: the old parent was gone or not active, so the branch is at the space top level. */
public record RestoreDTO(boolean movedToTop) {
}
