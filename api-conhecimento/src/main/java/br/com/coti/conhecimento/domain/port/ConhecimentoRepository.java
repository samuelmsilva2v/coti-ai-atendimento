package br.com.coti.conhecimento.domain.port;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConhecimentoRepository {

    void inserir(Conhecimento conhecimento);

    Optional<Conhecimento> buscarPorId(UUID id);

    /** Ordenado por data de criação decrescente; {@code categoria} vazia lista todas. */
    List<ResumoConhecimento> listar(Optional<Categoria> categoria, int pagina, int tamanho);

    long contar(Optional<Categoria> categoria);
}
