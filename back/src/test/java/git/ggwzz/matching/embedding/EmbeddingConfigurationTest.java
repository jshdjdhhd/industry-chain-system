package git.ggwzz.matching.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingConfigurationTest {

    @Test
    void applicationYaml_shouldDefaultToOllamaQwen3Embedding() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertEquals("${LOCAL_EMBEDDING_ENDPOINT:http://localhost:11434/v1/embeddings}",
                properties.getProperty("matching.embedding.endpoint"));
        assertEquals("${LOCAL_EMBEDDING_MODEL:qwen3-embedding:0.6b}",
                properties.getProperty("matching.embedding.model"));
        assertEquals("${LOCAL_EMBEDDING_DIMENSION:1024}",
                properties.getProperty("matching.embedding.dimension"));
    }

    @Test
    void embeddingProperties_shouldDefaultToOllamaQwen3Embedding() {
        EmbeddingProperties properties = new EmbeddingProperties();

        assertEquals("http://localhost:11434/v1/embeddings", properties.getEndpoint());
        assertEquals("qwen3-embedding:0.6b", properties.getModel());
        assertEquals(1024, properties.getDimension());
    }
}
