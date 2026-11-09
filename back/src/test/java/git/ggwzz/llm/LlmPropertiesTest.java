package git.ggwzz.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmPropertiesTest {

    @Test
    void defaults_shouldUseMimoOpenAiCompatibleConfig() {
        LlmProperties properties = new LlmProperties();

        assertEquals("mimo", properties.getProvider());
        assertEquals("https://api.xiaomimimo.com/v1", properties.getBaseUrl());
        assertEquals("mimo-v2.5-pro-ultraspeed", properties.getModel());
        assertEquals(20, properties.getTimeoutSeconds());
        assertEquals(0.0D, properties.getTemperature());
        assertEquals(700, properties.getMaxTokens());
    }
}
