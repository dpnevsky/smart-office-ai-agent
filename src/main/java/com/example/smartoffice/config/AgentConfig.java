package com.example.smartoffice.config;

import com.example.smartoffice.ai.OfficeAgent;
import com.example.smartoffice.ai.OfficeTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class AgentConfig {

    @Bean
    OfficeAgent officeAgent(
            ChatModel chatModel,
            OfficeTools officeTools,
            ObjectProvider<ContentRetriever> contentRetrieverProvider,
            Clock clock
    ) {
        var builder = AiServices.builder(OfficeAgent.class)
                .chatModel(chatModel)
                .toolProvider(officeToolProvider(officeTools))
                .systemMessageProvider(ignored -> systemPrompt(clock))
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(20)
                                .alwaysKeepSystemMessageFirst(true)
                                .build());

        ContentRetriever retriever = contentRetrieverProvider.getIfAvailable();
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }

        return builder.build();
    }

    static ToolProvider officeToolProvider(OfficeTools officeTools) {
        List<AiServiceTool> allTools = ToolService.findTools(officeTools);
        return request -> {
            Boolean explicitConfirmation = request.invocationParameters().get("explicitConfirmation");
            List<AiServiceTool> availableTools = allTools.stream()
                    .filter(tool -> Boolean.TRUE.equals(explicitConfirmation)
                            || !tool.name().equals("confirmPendingBooking"))
                    .toList();
            return new ToolProviderResult(availableTools);
        };
    }

    static String systemPrompt(Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        return """
                Ты AI-агент Smart Office. Отвечай по-русски, кратко и по делу.

                Текущая дата в офисе: %s.
                Часовой пояс: Europe/Moscow.
                Слово «завтра» означает строго %s.

                Твои обязанности:
                1. Находить свободные переговорные с помощью инструментов. Никогда не выдумывай свободные комнаты.
                2. Для бронирования сначала вызови requestRoomBooking.
                3. После requestRoomBooking обязательно остановись и попроси пользователя явно подтвердить действие.
                4. confirmPendingBooking можно вызывать только в НОВОМ ходе после явного сообщения пользователя вроде
                   «да», «подтверждаю», «забронируй», «ок, подтверждаю».
                5. Никогда не вызывай requestRoomBooking и confirmPendingBooking в одном пользовательском ходе.
                6. Если пользователь отказался — вызови cancelPendingBooking.
                7. Если не хватает даты, времени, продолжительности или количества участников — уточни недостающие данные.
                8. Если пользователь не сообщил имя, для демонстрации используй employee=demo-user и скажи об этом.
                9. Если подключён RAG-контекст с правилами офиса, используй его для ответов о правилах, но не придумывай нормы,
                   которых нет в найденном контексте.
                10. Никогда не сообщай, что бронирование создано, подтверждено или успешно выполнено, если
                    confirmPendingBooking не был успешно выполнен. Если изменяющий состояние инструмент не был вызван
                    или вернул ошибку, прямо скажи, что операция не выполнена.

                При выборе переговорной предпочитай наименьшую подходящую по вместимости, чтобы не занимать большую комнату без необходимости.
                """.formatted(today, tomorrow);
    }
}
