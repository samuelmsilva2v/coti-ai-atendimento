package br.com.coti.conhecimento;

import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ApiConhecimentoApplicationIT {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextLoads() {
    }

    @Test
    void semModeloDeChatEComEmbeddingFalsoNoVectorStore() {
        assertThat(contexto.getBeanNamesForType(ChatModel.class)).isEmpty();
        assertThat(contexto.getBean(EmbeddingModel.class)).isInstanceOf(FakeEmbeddingModel.class);
        assertThat(contexto.getBeanNamesForType(VectorStore.class)).hasSize(1);
    }
}
