package com.example.smartoffice.config;

import chat.giga.client.auth.AuthClient;
import chat.giga.langchain4j.GigaChatEmbeddingModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class RagConfig {

    @Bean
    EmbeddingModel embeddingModel(AuthClient authClient, GigaChatProperties properties) {
        return GigaChatEmbeddingModel.builder()
                .authClient(authClient)
                .verifySslCerts(properties.verifySsl())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    ContentRetriever officeRulesRetriever(
            EmbeddingModel embeddingModel,
            @Value("classpath:rag/office-rules.md") Resource rulesResource
    ) throws IOException {
        String rules = new String(rulesResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Document document = Document.from(rules);

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);

        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(3)
                .minScore(0.55)
                .build();
    }
}
