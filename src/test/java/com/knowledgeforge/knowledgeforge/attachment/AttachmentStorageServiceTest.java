package com.knowledgeforge.knowledgeforge.attachment;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AttachmentStorageServiceTest {

    GridFsOperations gridFs = mock(GridFsOperations.class);
    AttachmentStorageService storage = new AttachmentStorageService(gridFs);

    @Test
    void storeNeverUsesTheUserFilename() {
        ObjectId id = new ObjectId();
        InputStream in = new ByteArrayInputStream(new byte[1]);
        when(gridFs.store(in, "attachment", "application/pdf")).thenReturn(id);

        assertThat(storage.store(in, "application/pdf")).isEqualTo(id.toHexString());
    }

    @Test
    void missingOrInvalidIdIs404() {
        when(gridFs.findOne(any())).thenReturn(null);
        for (String id : new String[]{new ObjectId().toHexString(), "not-an-object-id"}) {
            assertThatThrownBy(() -> storage.load(id))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Test
    void deleteIgnoresNullAndInvalidIds() {
        storage.delete(null);
        storage.delete("bad");
        verifyNoInteractions(gridFs);

        storage.delete(new ObjectId().toHexString());
        verify(gridFs).delete(any());
    }
}
