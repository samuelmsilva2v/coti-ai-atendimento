package br.com.coti.atendimentos.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** ChatModel de teste: nunca chama a OpenAI; devolve respostas programadas e guarda os prompts recebidos. */
public class StubChatModel implements ChatModel {

    private final List<Prompt> recebidos = new CopyOnWriteArrayList<>();
    private volatile Supplier<String> resposta = () -> "{\"resposta\":\"ok\",\"respondida\":false,\"fontesUsadas\":[]}";

    @Override
    public ChatResponse call(Prompt prompt) {
        recebidos.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(resposta.get()))));
    }

    public void responder(String json) {
        resposta = () -> json;
    }

    public void falhar(RuntimeException erro) {
        resposta = () -> {
            throw erro;
        };
    }

    public List<Prompt> recebidos() {
        return recebidos;
    }

    public void reiniciar() {
        recebidos.clear();
        responder("{\"resposta\":\"ok\",\"respondida\":false,\"fontesUsadas\":[]}");
    }
}
