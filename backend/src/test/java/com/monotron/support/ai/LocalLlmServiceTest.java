package com.monotron.support.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LocalLlmServiceTest {

    @Test
    void answerDoesNotRepeatDocumentTitles() {
        LocalLlmService service = new LocalLlmService();

        LlmService.ChatResult result = service.complete(new LlmService.ChatRequest(
                "You are a support assistant.",
                "What is the refund policy?",
                List.of(
                        new LlmService.ContextSnippet(
                                "Refund Policy.pdf",
                                "The refund policy allows refunds within 30 days.",
                                0.9),
                        new LlmService.ContextSnippet(
                                "Refund Policy.pdf",
                                "Contact support to start a refund request.",
                                0.8)),
                0.2));

        assertThat(result.content()).contains("refund policy");
        assertThat(result.content()).doesNotContain("Refund Policy.pdf");
    }
}
