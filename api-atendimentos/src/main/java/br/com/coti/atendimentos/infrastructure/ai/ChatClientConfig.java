package br.com.coti.atendimentos.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Único {@link ChatClient} do serviço, com o prompt de sistema versionado e <b>sem ferramentas</b>: a busca
 * é feita pela aplicação antes da geração (ADR-005), e uma injeção de prompt não tem ações a disparar (Art. 10).
 */
@Configuration(proxyBeanMethods = false)
class ChatClientConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, @Value("classpath:prompts/sistema.st") Resource sistema) {
        return builder.defaultSystem(sistema).build();
    }
}
