package com.monotron.support.ai;

import java.util.Optional;

import com.monotron.support.ai.dto.AnswerDto;
import com.monotron.support.ai.dto.AnswerRequest;
import com.monotron.support.common.ForbiddenException;
import com.monotron.support.inbox.ConversationService;
import com.monotron.support.security.AuthPrincipal;
import com.monotron.support.tenancy.AppService;
import com.monotron.support.tenancy.dto.AppDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieval augmented generation over the tenant's knowledge base.
 */
@RestController
public class AiController {

    private final RagService ragService;
    private final AppService appService;
    private final ConversationService conversationService;

    public AiController(RagService ragService, AppService appService, ConversationService conversationService) {
        this.ragService = ragService;
        this.appService = appService;
        this.conversationService = conversationService;
    }

    @PostMapping("/api/ai/answer")
    public AnswerDto answer(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody AnswerRequest request) {
        if (principal == null || !principal.hasWorkspace()) {
            throw new ForbiddenException("No workspace is selected for this session");
        }

        Optional<AppDto> app = appService.findCurrent();
        double temperature = app.map(AppDto::aiTemperature).orElse(0.2);
        String systemPrompt = app.map(AppDto::aiSystemPrompt).orElse(null);

        String question = request.question();
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            // Ground the question in the thread when the caller points at one.
            conversationService.load(request.conversationId());
        }

        return ragService.answer(question, systemPrompt, temperature);
    }
}
