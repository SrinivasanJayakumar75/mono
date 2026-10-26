package com.monotron.support.ai;

import java.util.ArrayList;
import java.util.List;

import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Chat completions from any OpenAI compatible {@code /chat/completions} endpoint.
 */
public class OpenAiLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);

    private final RestClient restClient;
    private final String model;

    public OpenAiLlmService(AppProperties properties) {
        AppProperties.Ai.OpenAi config = properties.getAi().getOpenai();
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.model = config.getChatModel();
    }

    @Override
    public ChatResult complete(ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", buildSystemPrompt(request)));
        messages.add(new ChatMessage("user", request.userPrompt()));

        ChatCompletionRequest payload = new ChatCompletionRequest(model, messages, request.temperature());
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                ChatChoice choice = response.choices().get(0);
                if (choice.message() != null && choice.message().content() != null) {
                    return new ChatResult(choice.message().content().trim(), provider(), model);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Chat completion failed, falling back to the local composer: {}", ex.getMessage());
        }
        return new LocalLlmService().complete(request);
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return model;
    }

    private String buildSystemPrompt(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(request.systemPrompt() == null ? "" : request.systemPrompt().trim());
        if (request.context() != null && !request.context().isEmpty()) {
            prompt.append("\n\nUse only the knowledge base excerpts below to answer. ")
                    .append("If the excerpts do not contain the answer, say so plainly and offer to escalate to a human agent. ")
                    .append("Cite the source titles you used.\n\n");
            int index = 1;
            for (LlmService.ContextSnippet snippet : request.context()) {
                prompt.append('[').append(index++).append("] ").append(snippet.title()).append('\n')
                        .append(snippet.content()).append("\n\n");
            }
        }
        return prompt.toString();
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    private record ChatCompletionResponse(List<ChatChoice> choices) {
    }

    private record ChatChoice(ChatMessage message) {
    }
}
