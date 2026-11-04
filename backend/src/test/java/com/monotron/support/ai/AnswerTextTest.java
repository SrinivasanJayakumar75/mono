package com.monotron.support.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.monotron.support.ai.dto.CitationDto;
import org.junit.jupiter.api.Test;

class AnswerTextTest {

    @Test
    void removesSourceAnnotationsAppendedToAnswers() {
        assertThat(AnswerText.stripSourceAnnotations(
                "The support hours are 9am to 6pm CET. (Source: Employee Handbook)"))
                .isEqualTo("The support hours are 9am to 6pm CET.");
    }

    @Test
    void removesEverySourceAnnotationAndKeepsTheAnswer() {
        assertThat(AnswerText.stripSourceAnnotations(
                "Refunds take two days. (Source: Refunds.pdf) Contact support to start one. (Sources: FAQ, Refunds.pdf)"))
                .isEqualTo("Refunds take two days. Contact support to start one.");
    }

    @Test
    void removesLeadingCitationTitleFromOldLocalAnswers() {
        CitationDto citation = new CitationDto(
                "faq (2)", "DOCUMENT", "doc-1", null, 0.9, null);

        assertThat(AnswerText.forAiMessage("faq (2): Reset the password from Settings.", List.of(citation)))
                .isEqualTo("Reset the password from Settings.");
    }

    @Test
    void leavesPlainAnswersAlone() {
        assertThat(AnswerText.stripSourceAnnotations("Reset the password from Settings > Security."))
                .isEqualTo("Reset the password from Settings > Security.");
    }
}
