package com.monotron.support.ai;

import com.monotron.support.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the AI providers.
 *
 * <p>With {@code monotron.ai.provider=auto} (the default) the remote provider
 * is used when an API key is configured, otherwise the bundled local
 * implementations take over. That keeps the platform fully usable — uploads are
 * still chunked, embedded and searchable, and the AI agent still answers from
 * the knowledge base — without requiring any external account.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    public EmbeddingService embeddingService(AppProperties properties) {
        String provider = resolveProvider(properties);
        if ("openai".equals(provider)) {
            log.info("Using the remote embedding provider ({})", properties.getAi().getOpenai().getEmbeddingModel());
            return new OpenAiEmbeddingService(properties);
        }
        log.info("Using the local embedding provider");
        return new LocalEmbeddingService();
    }

    @Bean
    @ConditionalOnMissingBean(LlmService.class)
    public LlmService llmService(AppProperties properties) {
        String provider = resolveProvider(properties);
        if ("openai".equals(provider)) {
            log.info("Using the remote chat provider ({})", properties.getAi().getOpenai().getChatModel());
            return new OpenAiLlmService(properties);
        }
        log.info("Using the local grounded answer composer");
        return new LocalLlmService();
    }

    private String resolveProvider(AppProperties properties) {
        String configured = properties.getAi().getProvider();
        String apiKey = properties.getAi().getOpenai().getApiKey();
        boolean hasKey = apiKey != null && !apiKey.isBlank();
        if ("openai".equalsIgnoreCase(configured)) {
            return "openai";
        }
        if ("local".equalsIgnoreCase(configured)) {
            return "local";
        }
        return hasKey ? "openai" : "local";
    }
}
