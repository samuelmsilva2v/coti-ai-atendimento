package br.com.coti.atendimentos.controller.dto;

import br.com.coti.atendimentos.domain.model.Resposta;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record RespostaAtendimentoResponse(
        UUID correlationId,
        String resposta,
        boolean respondida,
        boolean fallback,
        List<FonteResposta> fontes,
        Instant respondidoEm) {

    public static RespostaAtendimentoResponse de(Resposta r, UUID correlationId) {
        return new RespostaAtendimentoResponse(correlationId, r.texto(), r.respondida(), r.isFallback(),
                r.fontes().stream().map(FonteResposta::de).toList(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
