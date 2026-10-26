package br.com.coti.conhecimento.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Servidor real em porta aleatória (o cliente MCP precisa de HTTP de verdade, não de MockMvc), com
 * PostgreSQL/pgvector via Testcontainers e {@link FakeEmbeddingModel}. Use {@link ClienteMcpDeTeste}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestcontainersConfig.class, FakeEmbeddingConfig.class})
public @interface McpIntegrationTest {
}
