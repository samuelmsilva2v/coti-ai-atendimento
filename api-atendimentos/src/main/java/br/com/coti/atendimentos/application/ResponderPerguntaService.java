package br.com.coti.atendimentos.application;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ModeloIndisponivelException;
import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.Desfecho;
import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Fonte;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.model.Resposta;
import br.com.coti.atendimentos.domain.model.SaidaModelo;
import br.com.coti.atendimentos.domain.model.TrechoContexto;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import br.com.coti.atendimentos.domain.port.GeradorDeResposta;
import br.com.coti.atendimentos.domain.port.MetricasAtendimento;
import br.com.coti.atendimentos.domain.port.MetricasAtendimento.Tipo;
import br.com.coti.atendimentos.domain.service.DerivadorDeFontes;
import br.com.coti.atendimentos.domain.service.MontadorDeContexto;
import br.com.coti.atendimentos.domain.service.VerificadorDeResposta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fluxo de atendimento (contrato, seção 6.1):
 * <ol>
 *   <li>busca na base via MCP, sempre e antes de qualquer geração (ADR-005); falha → 503, nunca fallback;</li>
 *   <li>monta o contexto; sem evidência que caiba → fallback, sem chamar o modelo (Art. 11);</li>
 *   <li>gera com o modelo; falha → 503 MODELO_INDISPONIVEL;</li>
 *   <li>deriva as fontes só das evidências; {@code respondida=false} ou sem fonte válida → fallback;</li>
 *   <li>verifica a resposta (valores sem fonte, vazamento do prompt) → fallback (seção 6.4, item 7).</li>
 * </ol>
 * Instanciado em {@code infrastructure.config.ServicosConfig}.
 */
public class ResponderPerguntaService {

    private static final Logger log = LoggerFactory.getLogger(ResponderPerguntaService.class);

    private final BuscadorDeConhecimento buscador;
    private final MontadorDeContexto montador;
    private final GeradorDeResposta gerador;
    private final DerivadorDeFontes derivador;
    private final VerificadorDeResposta verificador;
    private final MetricasAtendimento metricas;
    private final int topK;
    private final String mensagemFallback;

    public ResponderPerguntaService(BuscadorDeConhecimento buscador, MontadorDeContexto montador,
            GeradorDeResposta gerador, DerivadorDeFontes derivador, VerificadorDeResposta verificador,
            MetricasAtendimento metricas, int topK, String mensagemFallback) {
        this.buscador = buscador;
        this.montador = montador;
        this.gerador = gerador;
        this.derivador = derivador;
        this.verificador = verificador;
        this.metricas = metricas;
        this.topK = topK;
        this.mensagemFallback = Objects.requireNonNull(mensagemFallback, "mensagemFallback");
    }

    public Resposta responder(Pergunta pergunta) {
        long inicio = System.nanoTime();
        try {
            Resposta resposta = executar(pergunta, inicio);
            metricas.registrarAtendimento(resposta.respondida() ? Tipo.RESPONDIDA : Tipo.FALLBACK, decorrido(inicio));
            return resposta;
        } catch (ConhecimentoIndisponivelException e) {
            metricas.registrarAtendimento(Tipo.CONHECIMENTO_INDISPONIVEL, decorrido(inicio));
            throw e;
        } catch (ModeloIndisponivelException e) {
            metricas.registrarAtendimento(Tipo.MODELO_INDISPONIVEL, decorrido(inicio));
            throw e;
        } catch (RuntimeException e) {
            metricas.registrarAtendimento(Tipo.ERRO_INTERNO, decorrido(inicio));
            throw e;
        }
    }

    private Resposta executar(Pergunta pergunta, long inicio) {
        List<Evidencia> evidencias = buscador.buscar(pergunta, topK);
        ContextoRag contexto = montador.montar(pergunta, evidencias);

        Resposta resposta;
        int fontesDescartadas = 0;
        Optional<VerificadorDeResposta.Recusa> recusa = Optional.empty();
        if (contexto.vazio()) {
            resposta = Resposta.fallback(mensagemFallback);
        } else {
            SaidaModelo saida = gerador.gerar(contexto);
            DerivadorDeFontes.Derivacao derivacao = derivador.derivar(saida, contexto);
            fontesDescartadas = derivacao.fontesDescartadas();
            metricas.registrarFontesDescartadas(fontesDescartadas);
            if (!derivacao.fallback()) {
                recusa = verificador.verificar(saida.resposta(), textosCitados(contexto, derivacao.fontes()));
            }
            // O texto gerado é descartado no fallback: nunca devolvido sem fonte válida ou com valor sem fonte.
            resposta = derivacao.fallback() || recusa.isPresent()
                    ? Resposta.fallback(mensagemFallback)
                    : new Resposta(saida.resposta(), Desfecho.RESPONDIDA, derivacao.fontes());
        }

        // Só metadados: nem a pergunta, nem os trechos, nem a resposta vão para o log (Art. 13).
        log.atInfo()
                .addKeyValue("tamanhoPergunta", pergunta.texto().length())
                .addKeyValue("trechosRecebidos", evidencias.size())
                .addKeyValue("trechosNoContexto", contexto.trechos().size())
                .addKeyValue("tamanhoContexto", contexto.tamanhoEmCaracteres())
                .addKeyValue("chunkIdsUsados", resposta.fontes().stream().map(Fonte::chunkId).toList())
                .addKeyValue("fontesDescartadas", fontesDescartadas)
                .addKeyValue("respostaRecusada", recusa.map(Enum::name).orElse(null))
                .addKeyValue("desfecho", resposta.desfecho())
                .addKeyValue("latenciaMs", decorrido(inicio).toMillis())
                .log("Atendimento concluído");
        return resposta;
    }

    private static List<String> textosCitados(ContextoRag contexto, List<Fonte> fontes) {
        Set<UUID> citados = fontes.stream().map(Fonte::chunkId).collect(Collectors.toSet());
        return contexto.trechos().stream()
                .map(TrechoContexto::evidencia)
                .filter(e -> citados.contains(e.chunkId()))
                .map(Evidencia::texto)
                .toList();
    }

    private static Duration decorrido(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio);
    }
}
