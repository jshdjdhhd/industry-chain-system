package git.ggwzz.matching.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchingLlmPropertiesTest {

    @Test
    void defaults_shouldInheritBaseAndAddMatchingThresholds() {
        MatchingLlmProperties properties = new MatchingLlmProperties();

        // 继承自 LlmProperties
        assertEquals("mimo", properties.getProvider());
        assertEquals("https://api.xiaomimimo.com/v1", properties.getBaseUrl());
        assertEquals("mimo-v2.5-pro-ultraspeed", properties.getModel());
        assertEquals(20, properties.getTimeoutSeconds());
        assertEquals(0.0D, properties.getTemperature());
        assertEquals(700, properties.getMaxTokens());

        // 挂链专用阈值
        assertEquals(6, properties.getCandidateTopK());
        assertEquals(3, properties.getResultTopK());
        assertEquals(0.82D, properties.getSkipRerankConfidenceThreshold());
        assertEquals(0.65D, properties.getMinResultConfidence());
        assertEquals(0.70D, properties.getClearWinnerMinConfidenceThreshold());
        assertEquals(0.08D, properties.getClearWinnerGapThreshold());
        assertEquals(0.90D, properties.getStrongRuleConfidenceThreshold());
        assertEquals(0.55D, properties.getStrongEvidenceMinVectorScore());
    }
}
