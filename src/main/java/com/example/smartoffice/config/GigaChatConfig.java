package com.example.smartoffice.config;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder.OAuthBuilder;
import chat.giga.langchain4j.GigaChatChatModel;
import chat.giga.langchain4j.GigaChatChatRequestParameters;
import chat.giga.model.Scope;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GigaChatProperties.class)
public class GigaChatConfig {

    @Bean
    AuthClient gigaChatAuthClient(GigaChatProperties properties) {
        if (properties.authKey() == null || properties.authKey().isBlank()) {
            throw new IllegalStateException(
                    "Не задан GIGACHAT_AUTH_KEY. Скопируйте .env.example в .env и укажите Authorization Key GigaChat API."
            );
        }

        return AuthClient.builder()
                .withOAuth(OAuthBuilder.builder()
                        .scope(Scope.GIGACHAT_API_PERS)
                        .authKey(properties.authKey())
                        .build())
                .build();
    }

    @Bean
    ChatModel gigaChatModel(AuthClient authClient, GigaChatProperties properties) {
        return GigaChatChatModel.builder()
                .authClient(authClient)
                .verifySslCerts(properties.verifySsl())
                .logRequests(properties.logRequests())
                .logResponses(properties.logResponses())
                .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                        .modelName(properties.model())
                        .temperature(0.2)
                        .build())
                .build();
    }
}
