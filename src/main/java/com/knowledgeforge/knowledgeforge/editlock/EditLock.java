package com.knowledgeforge.knowledgeforge.editlock;

import com.knowledgeforge.knowledgeforge.common.UserRef;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/** Soft edit lock (physical schema 3.7), Release 1a only. _id equals the node id. */
@Getter @Setter
@Document(collection = "edit_locks")
public class EditLock {
    @Id
    private String id;
    private UserRef holder;
    private Date expiresAt;
}
