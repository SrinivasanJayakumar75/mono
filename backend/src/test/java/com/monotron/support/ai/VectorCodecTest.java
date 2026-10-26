package com.monotron.support.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorCodecTest {

    @Test
    @DisplayName("a vector is rendered as a pgvector literal")
    void rendersLiteral() {
        assertThat(VectorCodec.toLiteral(new float[] { 0.5f, -0.25f, 0f }))
                .isEqualTo("[0.5000000,-0.2500000,0.0000000]");
    }

    @Test
    @DisplayName("non finite values are replaced with zero so the literal stays valid")
    void sanitisesNonFiniteValues() {
        String literal = VectorCodec.toLiteral(
                new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1f });
        assertThat(literal).isEqualTo("[0,0,0,1.0000000]");
    }

    @Test
    @DisplayName("an empty vector is still a valid literal")
    void handlesEmptyVector() {
        assertThat(VectorCodec.toLiteral(new float[0])).isEqualTo("[]");
    }
}
