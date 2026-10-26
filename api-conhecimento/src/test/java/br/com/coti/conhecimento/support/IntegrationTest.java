package br.com.coti.conhecimento.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contexto completo com PostgreSQL/pgvector via Testcontainers e {@link FakeEmbeddingModel}
 * no lugar da OpenAI. Todas as classes usam a mesma
 * configuração para compartilhar um único contexto (e um único container) em cache.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestcontainersConfig.class, FakeEmbeddingConfig.class})
public @interface IntegrationTest {
}
