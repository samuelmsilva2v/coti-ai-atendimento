package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.exception.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.Pagina;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;
import br.com.coti.conhecimento.domain.port.ConhecimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ConsultarConhecimentoService {

    private final ConhecimentoRepository repositorio;

    public ConsultarConhecimentoService(ConhecimentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Conhecimento buscarPorId(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new ConhecimentoNaoEncontradoException(id));
    }

    public Pagina<ResumoConhecimento> listar(Optional<Categoria> categoria, int pagina, int tamanho) {
        return new Pagina<>(repositorio.listar(categoria, pagina, tamanho), pagina, tamanho,
                repositorio.contar(categoria));
    }
}
