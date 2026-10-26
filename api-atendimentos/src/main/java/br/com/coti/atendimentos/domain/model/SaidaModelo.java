package br.com.coti.atendimentos.domain.model;

import java.util.List;

/**
 * Saída estruturada pedida ao modelo (contrato, seção 6.4). É interna e nunca exposta na API;
 * {@code fontesUsadas} contém ids locais {@code Tn}, validados pelo {@code DerivadorDeFontes}.
 */
public record SaidaModelo(String resposta, boolean respondida, List<String> fontesUsadas) {

    public SaidaModelo {
        fontesUsadas = fontesUsadas == null ? List.of() : List.copyOf(fontesUsadas);
    }
}
