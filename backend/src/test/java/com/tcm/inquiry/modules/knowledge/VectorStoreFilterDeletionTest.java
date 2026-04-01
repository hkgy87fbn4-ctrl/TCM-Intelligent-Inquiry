package com.tcm.inquiry.modules.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

@ExtendWith(MockitoExtension.class)
class VectorStoreFilterDeletionTest {

    @Mock private VectorStore vectorStore;

    @Test
    void deleteByFilterInvokesDeleteWithMatchingIds() {
        Document doc = org.mockito.Mockito.mock(Document.class);
        when(doc.getId()).thenReturn("chunk-1");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        VectorStoreFilterDeletion deletion = new VectorStoreFilterDeletion(vectorStore);
        deletion.deleteByFilter(new FilterExpressionBuilder().eq("file_id", "abc").build());

        verify(vectorStore).delete(List.of("chunk-1"));
    }

    @Test
    void deleteByFilterSkipsDeleteWhenNoMatches() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        VectorStoreFilterDeletion deletion = new VectorStoreFilterDeletion(vectorStore);
        deletion.deleteByFilter(new FilterExpressionBuilder().eq("kb_id", "1").build());

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        org.mockito.Mockito.verifyNoMoreInteractions(vectorStore);
    }
}
