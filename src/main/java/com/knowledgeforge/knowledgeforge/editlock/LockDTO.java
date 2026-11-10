package com.knowledgeforge.knowledgeforge.editlock;

import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.util.Date;

/** mine=false means someone else holds it (holder says who). */
public record LockDTO(boolean mine, UserRef holder, Date expiresAt) {
}
