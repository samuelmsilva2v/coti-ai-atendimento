package br.com.coti.atendimentos.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Substitui o OpenAiChatModel nos testes de integração (Art. 14: sem OpenAI real). */
@TestConfiguration(proxyBeanMethods = false)
public class StubChatModelConfig {

    @Bean
    @Primary
    StubChatModel stubChatModel() {
        return new StubChatModel();
    }
}
