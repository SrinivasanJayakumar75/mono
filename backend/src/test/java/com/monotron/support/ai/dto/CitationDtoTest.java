package com.monotron.support.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CitationDtoTest {

    @Test
    void keepsOneCitationPerDocumentUsingTheBestScore() {
        List<CitationDto> citations = List.of(
                new CitationDto("Pricing.pdf", "DOCUMENT", "doc-1", null, 0.62, "first chunk"),
                new CitationDto("Pricing.pdf", "DOCUMENT", "doc-1", null, 0.81, "better chunk"),
                new CitationDto("Refunds.pdf", "DOCUMENT", "doc-2", null, 0.55, "refund chunk"));

        List<CitationDto> deduped = CitationDto.dedupe(citations);

        assertThat(deduped).hasSize(2);
        assertThat(deduped.get(0).sourceId()).isEqualTo("doc-1");
        assertThat(deduped.get(0).score()).isEqualTo(0.81);
        assertThat(deduped.get(0).snippet()).isEqualTo("better chunk");
        assertThat(deduped.get(1).sourceId()).isEqualTo("doc-2");
    }

    @Test
    void keepsDifferentSourcesEvenWhenTitlesMatch() {
        List<CitationDto> citations = List.of(
                new CitationDto("FAQ", "ARTICLE", "article-1", null, 0.4, "one"),
                new CitationDto("FAQ", "ARTICLE", "article-2", null, 0.3, "two"));

        assertThat(CitationDto.dedupe(citations)).hasSize(2);
    }
}
