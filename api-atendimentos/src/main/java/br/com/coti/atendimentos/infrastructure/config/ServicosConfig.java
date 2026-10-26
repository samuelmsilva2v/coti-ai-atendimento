package br.com.coti.atendimentos.infrastructure.config;

import br.com.coti.atendimentos.application.ResponderPerguntaService;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import br.com.coti.atendimentos.domain.port.GeradorDeResposta;
import br.com.coti.atendimentos.domain.port.MetricasAtendimento;
import br.com.coti.atendimentos.domain.service.DerivadorDeFontes;
import br.com.coti.atendimentos.domain.service.MontadorDeContexto;
import br.com.coti.atendimentos.domain.service.VerificadorDeResposta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Monta os casos de uso com a configuração, sem que domínio e aplicação dependam da infraestrutura. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AtendimentoProperties.class, RagProperties.class})
class ServicosConfig {

    @Bean
    ResponderPerguntaService responderPerguntaService(BuscadorDeConhecimento buscador, GeradorDeResposta gerador,
            MetricasAtendimento metricas, RagProperties rag, AtendimentoProperties atendimento,
            @Value("classpath:prompts/sistema.st") Resource promptDeSistema) throws IOException {
        return new ResponderPerguntaService(buscador, new MontadorDeContexto(rag.contextoMaxCaracteres()), gerador,
                new DerivadorDeFontes(), new VerificadorDeResposta(promptDeSistema.getContentAsString(StandardCharsets.UTF_8)),
                metricas, rag.topK(), atendimento.mensagemFallback());
    }
}
