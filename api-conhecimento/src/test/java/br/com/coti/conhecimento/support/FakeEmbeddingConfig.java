package br.com.coti.conhecimento.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Substitui o OpenAiEmbeddingModel nos testes de integração (Art. 14: sem OpenAI real). */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingConfig {

    @Bean
    @Primary
    FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
