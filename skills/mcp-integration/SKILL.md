---
name: mcp-integration
description: Orienta a integração por MCP no COTI AI Atendimento com Spring AI 2.0.1 — MCP Server na api-conhecimento e MCP Client na api-atendimentos, transporte Streamable HTTP, descoberta e execução da ferramenta de busca, validação de parâmetros, timeouts e erros. Use ao criar ou alterar a ferramenta MCP, o cliente MCP ou qualquer comunicação entre os dois serviços.
---

# Skill: Integração MCP (Server e Client)

> Base: Spring AI **2.0.1**, Spring Boot 4.0.x, MCP sobre HTTP com transporte **Streamable HTTP**.
> Constituição: Art. 4, 5, 6, 10, 11, 13, 16.
> O contrato da ferramenta (nome, descrição, parâmetros, resposta, erros) é definido em
> `specs/06-api-contracts.md`. Os nomes usados abaixo (`buscar_conhecimento` e campos) são
> **exemplos**; em caso de diferença, vale o contrato.

## Quando usar

- Criar ou alterar a ferramenta de busca semântica exposta pela `api-conhecimento`.
- Configurar o MCP Client da `api-atendimentos` e a chamada à ferramenta.
- Definir timeouts, tratamento de indisponibilidade e mapeamento de erros entre os serviços.
- Escrever testes de contrato da ferramenta MCP (junto com a skill `testing`).

## Responsabilidades

| Lado | Serviço | Responsabilidade |
|---|---|---|
| MCP Server | `api-conhecimento` | Publicar a ferramenta de busca; validar parâmetros; executar a busca (skill `rag-pgvector`); devolver resultados estruturados com fontes e scores. |
| MCP Client | `api-atendimentos` | Conectar ao servidor via Streamable HTTP; verificar a ferramenta disponível; invocá-la com a pergunta; converter o resultado em objetos de domínio; tratar timeout e falhas. |

## Passo a passo — MCP Server (`api-conhecimento`)

1. **Dependência:**

   ```xml
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
   </dependency>
   ```

2. **Configuração Streamable HTTP** (SSE está depreciado no 2.0):

   ```yaml
   spring:
     ai:
       mcp:
         server:
           protocol: STREAMABLE
           name: api-conhecimento
           version: 1.0.0
           type: SYNC
           instructions: "Busca semântica na base de conhecimento da COTI Informática."
           streamable-http:
             mcp-endpoint: /mcp
   ```

3. **Ferramenta com anotações** (`org.springframework.ai.mcp.annotation`). A classe da ferramenta
   é um adaptador fino: valida, delega ao serviço de busca e converte o resultado.

   ```java
   @Component
   class BuscaConhecimentoTool {

       private final BuscaSemanticaService busca;

       BuscaConhecimentoTool(BuscaSemanticaService busca) { this.busca = busca; }

       @McpTool(name = "buscar_conhecimento",
                description = "Busca trechos relevantes da base de conhecimento da COTI para uma pergunta de aluno.")
       public ResultadoBusca buscarConhecimento(
               @McpToolParam(description = "Pergunta do aluno em linguagem natural", required = true)
               String pergunta,
               @McpToolParam(description = "Quantidade máxima de trechos (1 a 10)", required = false)
               Integer limite) {
           return busca.buscar(ConsultaConhecimento.of(pergunta, limite));
       }
   }

   record ResultadoBusca(List<TrechoEncontrado> trechos) {}
   record TrechoEncontrado(String sourceId, String titulo, String conteudo, double score) {}
   ```

   - O retorno é um `record` (serializado como JSON estruturado), nunca texto livre.
   - O retorno traz texto e metadados; **nunca vetores** (Art. 6).
   - A descrição da ferramenta e dos parâmetros é parte do contrato: mudar exige a regra 11 do
     `AGENTS.md`.

4. **Validação de parâmetros** no objeto de consulta (`ConsultaConhecimento`), antes de gerar
   embedding:
   - `pergunta` obrigatória, não vazia após `trim`, com tamanho máximo definido no contrato;
   - `limite` opcional, com valor padrão e faixa (ex.: 1 a 10) definidos no contrato;
   - entrada inválida gera erro de ferramenta (`isError=true` no resultado) com mensagem curta e
     sem detalhes internos. Não lance stack trace para o cliente.

5. **Erros do servidor:** falha de banco ou de embedding vira erro de ferramenta com código e
   mensagem genérica definidos no contrato; o detalhe técnico fica só no log, com o id de
   correlação.

## Passo a passo — MCP Client (`api-atendimentos`)

1. **Dependência:**

   ```xml
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-starter-mcp-client</artifactId>
   </dependency>
   ```

2. **Configuração Streamable HTTP**, com URL vinda de variável de ambiente:

   ```yaml
   spring:
     ai:
       mcp:
         client:
           name: api-atendimentos
           type: SYNC
           request-timeout: ${MCP_REQUEST_TIMEOUT:10s}
           initialized: false          # não derruba a inicialização se o servidor estiver fora
           toolcallback:
             enabled: false            # a ferramenta não é entregue ao modelo (ver passo 3)
           streamable-http:
             connections:
               conhecimento:
                 url: ${MCP_CONHECIMENTO_URL}
   ```

   `MCP_CONHECIMENTO_URL` e `MCP_REQUEST_TIMEOUT` entram no `.env.example`. Os valores oficiais
   ficam em `specs/03-architecture.md`. Ao ligar `initialized: false`, o adaptador DEVE chamar
   `initialize()` de forma preguiçosa antes do primeiro uso.

3. **Invocação explícita pela aplicação.** A busca é um passo determinístico do fluxo RAG
   (buscar → avaliar → gerar), não uma decisão do modelo. Crie um adaptador de infraestrutura
   que implementa a porta de domínio (ex.: `BuscadorDeConhecimento`) usando o `McpSyncClient`
   auto-configurado:

   ```java
   @Component
   class McpBuscadorDeConhecimento implements BuscadorDeConhecimento {

       private final McpSyncClient cliente;
       private final JsonMapper json;   // Jackson 3 (tools.jackson), padrão do Spring Boot 4

       McpBuscadorDeConhecimento(List<McpSyncClient> clientes, JsonMapper json) {
           this.cliente = clientes.getFirst();   // uma única conexão: "conhecimento"
           this.json = json;
       }

       @Override
       public List<TrechoDeConhecimento> buscar(String pergunta) {
           var request = new McpSchema.CallToolRequest("buscar_conhecimento", Map.of("pergunta", pergunta));
           McpSchema.CallToolResult result = cliente.callTool(request);
           if (Boolean.TRUE.equals(result.isError())) {
               throw new ConhecimentoIndisponivelException("ferramenta retornou erro");
           }
           return converter(result);   // structuredContent ou o TextContent JSON
       }
   }
   ```

   Ajuste a construção de `CallToolRequest` e a leitura do resultado à API do SDK MCP Java
   gerenciado pelo BOM (confira com Context7 antes de implementar).

4. **Descoberta da ferramenta.** Na primeira utilização (ou no health check), chame
   `listTools()` e verifique que `buscar_conhecimento` existe. Ausência da ferramenta é falha de
   contrato: registre em log e trate como indisponibilidade.

5. **Timeouts e falhas.** `request-timeout` limita cada chamada. Timeout, conexão recusada,
   `isError=true` ou resposta fora do formato viram `ConhecimentoIndisponivelException` →
   erro padronizado do contrato (ex.: 503). Nunca gere resposta sem contexto nesses casos
   (Art. 11).

6. **Correlação.** Propague o id de correlação da requisição REST para a chamada MCP (por
   exemplo como cabeçalho HTTP no transporte ou em `_meta` da requisição, conforme o contrato) e
   registre-o nos logs dos dois lados (Art. 13).

7. **Health check.** Exponha no Actuator um indicador de saúde que verifica a conectividade com
   o MCP Server (ping/listTools com timeout curto).

## Padrões de codificação

- Classes MCP ficam na camada de infraestrutura/API; o domínio só conhece portas
  (`BuscadorDeConhecimento`, `BuscaSemanticaService`).
- Entradas e saídas da ferramenta como `record`.
- Nomes, descrições e campos da ferramenta iguais aos de `06-api-contracts.md`.
- Cliente `SYNC` (projeto em Spring MVC); não misture clientes SYNC e ASYNC.
- Toda configuração de URL e timeout vem de variáveis de ambiente.

## Antipadrões proibidos

- Endpoints REST internos, acesso ao banco, arquivos ou filas para trocar conhecimento (Art. 4).
- Protocolo SSE (depreciado) ou STDIO entre os serviços.
- Entregar a ferramenta ao modelo (`defaultTools(mcpTools)`) e deixar o LLM decidir se busca.
- Expor ferramentas com escrita ou efeito colateral ao modelo (Art. 10).
- Trafegar vetores ou embeddings no contrato MCP (Art. 6).
- Devolver texto livre não estruturado ou stack traces como resultado da ferramenta.
- Ignorar `isError` ou tratar timeout como "sem resultados" (isso esconderia uma falha atrás do
  fallback).
- Alterar nome, descrição ou esquema da ferramenta sem atualizar o contrato antes (Art. 16).
- URL do servidor fixa no código.

## Critérios de validação

- Teste de integração: o cliente lista e invoca a ferramenta contra um servidor MCP real
  (aplicação de teste) via Streamable HTTP.
- Teste de contrato: nome, descrição, esquema de entrada (campos obrigatórios) e formato de saída
  batem com `06-api-contracts.md`.
- Testes do servidor: parâmetro ausente, vazio ou fora da faixa gera erro de ferramenta, sem
  chamar o `EmbeddingModel`.
- Testes do cliente: timeout, servidor fora do ar e `isError=true` geram o erro padronizado e o
  modelo não é chamado.
- A `api-atendimentos` não contém cliente HTTP apontando para endpoints REST da
  `api-conhecimento`.

## Checklist de conclusão

- [ ] `spring-ai-starter-mcp-server-webmvc` com `protocol: STREAMABLE` na `api-conhecimento`.
- [ ] `spring-ai-starter-mcp-client` com conexão `streamable-http` na `api-atendimentos`.
- [ ] Ferramenta com `@McpTool`/`@McpToolParam`, nome e esquema iguais ao contrato.
- [ ] Validação de parâmetros no servidor.
- [ ] Invocação explícita pelo adaptador; `toolcallback.enabled=false`.
- [ ] Timeout configurado e falhas mapeadas para o erro do contrato.
- [ ] Id de correlação propagado e logado.
- [ ] Health check do MCP Server na `api-atendimentos`.
- [ ] Variáveis novas no `.env.example`.
- [ ] Testes de integração e de contrato executados nesta interação.
