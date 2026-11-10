package com.knowledgeforge.knowledgeforge.node;

import java.util.List;

/** {@code canPurge}: the caller may delete forever (team ADMIN, or owner of a personal space). */
public record BranchListDTO(boolean canPurge, List<BranchDTO> items) {
}
