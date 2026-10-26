package br.com.coti.atendimentos;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiAtendimentosApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextLoads() {
    }

    @Test
    void temChatMasNenhumEmbeddingModel() {   // T051, Art. 6
        assertThat(contexto.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(ChatModel.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(ChatClient.class)).hasSize(1);
    }

    @Test
    void naoCriaModelosDeAudioImagemNemModeracao() {
        assertThat(contexto.getBeanNamesForType(org.springframework.ai.audio.tts.TextToSpeechModel.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(org.springframework.ai.image.ImageModel.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(org.springframework.ai.moderation.ModerationModel.class)).isEmpty();
    }
}
