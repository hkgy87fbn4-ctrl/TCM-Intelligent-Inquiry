package com.tcm.inquiry.modules.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock private KnowledgeBaseRepository knowledgeBaseRepository;

    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(knowledgeBaseRepository, "bge-m3:latest");
    }

    @Test
    void createBaseUsesDefaultEmbeddingWhenNull() {
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                .thenAnswer(
                        inv -> {
                            KnowledgeBase k = inv.getArgument(0);
                            k.setId(1L);
                            return k;
                        });

        knowledgeService.createBase("n", null);

        ArgumentCaptor<KnowledgeBase> cap = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseRepository).save(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("n");
        assertThat(cap.getValue().getEmbeddingModelName()).isEqualTo("bge-m3:latest");
        assertThat(cap.getValue().getVectorBackend()).isEqualTo(VectorBackend.OLLAMA);
    }

    @Test
    void createBaseTrimsCustomEmbedding() {
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
                .thenAnswer(
                        inv -> {
                            KnowledgeBase k = inv.getArgument(0);
                            k.setId(2L);
                            return k;
                        });

        knowledgeService.createBase("x", "  my-embed  ");

        ArgumentCaptor<KnowledgeBase> cap = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseRepository).save(cap.capture());
        assertThat(cap.getValue().getEmbeddingModelName()).isEqualTo("my-embed");
    }

    @Test
    void listBasesDelegatesToRepository() {
        when(knowledgeBaseRepository.findAll()).thenReturn(List.of());
        assertThat(knowledgeService.listBases()).isEmpty();
        verify(knowledgeBaseRepository).findAll();
    }
}
