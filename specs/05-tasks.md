# Tarefas — COTI AI Atendimento (MVP)

> Tarefas atômicas do [`04-plan.md`](04-plan.md), em ordem de dependência. Cada tarefa segue o
> fluxo **Spec → Plan → Implement → Test → Review** do `AGENTS.md` e só é marcada como concluída
> depois que a **verificação** for executada nesta interação, com saída real no relatório.

**Legenda:**
- **Dep.** são as tarefas que precisam estar concluídas antes.
- **Verif.** é como provar que a tarefa está pronta.
- **CA/RN** são os critérios de aceite e as regras de negócio de `02-requirements.md`.
- `[C]` indica `api-conhecimento`, `[A]` indica `api-atendimentos`, `[R]` indica a raiz ou a
  infraestrutura.

**Convenção para a `api-conhecimento`:** a partir da T014, todo `./mvnw verify` nela exige o
Docker Desktop ativo (`docker info`).

---

## Fase 1 — Infraestrutura Docker e PostgreSQL/pgvector

- [x] **T001** [R] Atualizar o `.env.example` com todas as variáveis de `03-architecture.md`,
  seção 9, sem valores reais e com `POSTGRES_PORT=5434`.
  Dep.: —
  Verif.: cada variável da tabela 9 aparece no arquivo; nenhum valor sensível preenchido;
  `git check-ignore .env` confirma que o `.env` é ignorado.

- [x] **T002** [R] Criar o `infra/docker-compose.yml` com o serviço `postgres`:
  - imagem `pgvector/pgvector:pg16`;
  - porta `127.0.0.1:${POSTGRES_PORT:-5434}:5432`;
  - `env_file` apontando para o `.env`;
  - volume `coti-pgdata`;
  - healthcheck com `pg_isready`.

  Dep.: T001
  Verif.: `docker compose -f infra/docker-compose.yml --env-file .env config` sem erros.

- [x] **T003** [R] Subir o PostgreSQL e confirmar o pgvector.
  Dep.: T002
  Verif.:
  - `docker info`;
  - `docker compose ... up -d`;
  - `docker compose ... ps` mostra `healthy`;
  - a consulta a `pg_available_extensions` mostra `vector`.

## Fase 2 — Estrutura Maven e inicialização dos microsserviços

- [x] **T004** [C] Criar o projeto `api-conhecimento`:
  - `pom.xml` com parent `spring-boot-starter-parent` 4.1.x (patch fixado), `java.version=21` e
    `spring-ai-bom` 2.0.1 importado;
  - dependências `spring-boot-starter-webmvc`, `-validation`, `-actuator`,
    `springdoc-openapi-starter-webmvc-ui` 3.x e `-test`;
  - Maven Wrapper, `ApiConhecimentoApplication` e `ApiConhecimentoApplicationTests`
    (`contextLoads`).

  Dep.: —
  Verif.: `cd api-conhecimento && ./mvnw verify`; o patch do Boot e do springdoc fica registrado
  em `03-architecture.md`, seção 2.

- [x] **T005** [C] Criar o `application.yml` da `api-conhecimento`:
  - porta 8081, `server.address=${SERVER_ADDRESS:127.0.0.1}`, `spring.application.name`;
  - logs ECS (`LOG_FORMAT`);
  - Actuator expondo `health,info,metrics`, com `show-details: never` e
    `show-components: always`;
  - `server.error.include-stacktrace=never`.

  Mais um teste HTTP que verifica `/actuator/health` com 200 e sem `details`, e `/v3/api-docs` com
  200.
  Dep.: T004
  Verif.: `./mvnw verify`; `./mvnw spring-boot:run` e `curl -i http://localhost:8081/actuator/health`.

- [x] **T006** [A] Criar o projeto `api-atendimentos`, igual à T004 mas **sem** nenhuma
  dependência de banco: `ApiAtendimentosApplication` e `ApiAtendimentosApplicationTests`.
  Dep.: —
  Verif.: `cd api-atendimentos && ./mvnw verify`.

- [x] **T007** [A] Criar o `application.yml` da `api-atendimentos`, igual à T005, com a porta
  **8082**, e o teste de health e api-docs.
  Dep.: T006
  Verif.: `./mvnw verify`; `curl -i http://localhost:8082/actuator/health`.

- [x] **T008** [A] Configurar o `maven-enforcer-plugin` com `bannedDependencies`:
  - `org.postgresql:*`;
  - `spring-boot-starter-jdbc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`;
  - `org.flywaydb:*`;
  - `spring-ai-starter-vector-store-*`, `spring-ai-pgvector-store`.

  Dep.: T006
  Verif.: `./mvnw verify` passa; adicionar temporariamente `org.postgresql:postgresql` faz o build
  falhar no enforcer (reverter em seguida).

- [x] **T009** [R] Criar o `pom.xml` agregador na raiz (`packaging=pom`, módulos
  `api-conhecimento` e `api-atendimentos`, **sem** ser parent) e o Maven Wrapper da raiz.
  Dep.: T004, T006
  Verif.: `./mvnw verify` na raiz compila e testa os dois módulos.

- [x] **T010** [C] Criar o `infrastructure.web.CorrelationIdFilter`:
  - lê `X-Correlation-Id`, aceitando só UUID válido, ou gera um novo;
  - coloca o id no MDC `correlationId`, devolve o cabeçalho e limpa o MDC no final.

  Mais o `CorrelationIdFilterTest`.
  Dep.: T005
  Verif.: os testes cobrem: ausente → gerado; UUID válido → mantido; inválido → substituído;
  cabeçalho devolvido; MDC limpo.

- [x] **T011** [C] Criar o `infrastructure.web.GlobalExceptionHandler`, que produz `ProblemDetail`
  com `type`, `title`, `status`, `detail`, `instance`, `codigo`, `correlationId`, `timestamp` e
  `erros[]`. Ele cobre:
  - `VALIDACAO_FALHOU` e `JSON_INVALIDO`;
  - `RECURSO_NAO_ENCONTRADO` (rota inexistente);
  - `METODO_NAO_SUPORTADO`;
  - `TIPO_MIDIA_NAO_SUPORTADO`;
  - `ERRO_INTERNO`.

  Mais o `GlobalExceptionHandlerTest`, que usa um controller só de teste.
  Dep.: T010
  Verif.: cada código gera o status e o `application/problem+json` do contrato, seção 2, sem stack
  trace.

- [x] **T012** [A] Criar o `CorrelationIdFilter` da `api-atendimentos`, igual à T010, com os
  testes.
  Dep.: T007
  Verif.: `CorrelationIdFilterTest` passa.

- [x] **T013** [A] Criar o `GlobalExceptionHandler` da `api-atendimentos`, igual à T011, com os
  testes.
  Dep.: T012
  Verif.: `GlobalExceptionHandlerTest` passa; `./mvnw verify` passa na raiz.

## Fase 3 — Persistência e ingestão de conhecimento

- [x] **T014** [C] Adicionar `postgresql`, o starter JDBC, `spring-boot-starter-flyway`,
  `flyway-database-postgresql`, `spring-boot-testcontainers`, `testcontainers-postgresql` e
  `testcontainers-junit-jupiter`. Configurar o datasource com variáveis de ambiente. Criar o
  `test/support/PostgresTestcontainersConfig` (`pgvector/pgvector:pg16` com `@ServiceConnection`)
  e ajustar o `contextLoads` para usá-lo.
  Dep.: T003, T011
  Verif.: `docker info`; `./mvnw verify` sobe o contexto com Testcontainers.

- [x] **T015** [C] Criar a migração `V1__extensoes.sql` (`vector`, `hstore`, `uuid-ossp`).
  Dep.: T014
  Verif.: o `MigracoesIT` confirma as três extensões instaladas.

- [x] **T016** [C] Criar a migração `V2__conhecimento.sql`, com a tabela `conhecimento` (colunas da
  seção 5.2 da arquitetura) e o índice `(categoria, criado_em DESC)`.
  Dep.: T015
  Verif.: o `MigracoesIT` confirma a tabela, as colunas e o índice em um banco vazio.

- [x] **T017** [C] Criar o domínio `Categoria` (enum), o `Conhecimento` (record), a porta
  `ConhecimentoRepository` e a `ConhecimentoNaoEncontradoException`.
  Dep.: T014
  Verif.: `./mvnw compile`; nenhuma classe de `domain` importa Spring Web ou JDBC.

- [x] **T018** [C] Criar o `JdbcConhecimentoRepository` com `JdbcClient`, com as operações
  inserir, buscar por id, listar paginado com filtro de categoria e ordem `criado_em DESC`, e
  contar. Mapear `text[]` e `timestamptz`.
  Dep.: T016, T017
  Verif.: o `JdbcConhecimentoRepositoryIT` cobre a inserção e a leitura completas, a paginação, o
  filtro e o id inexistente.

- [x] **T019** [C] Criar o `AdminProperties` (record com `@Validated` e token obrigatório) e o
  `AdminTokenFilter`, que protege `/api/conhecimentos/**`:
  - compara o token com `MessageDigest.isEqual`;
  - devolve `401 NAO_AUTORIZADO` com `WWW-Authenticate`;
  - roda antes da desserialização;
  - nunca loga o valor.

  Dep.: T011
  Verif.: o `AdminTokenFilterTest` cobre a ausência, o valor errado e o valor certo (CA01.5); o
  teste de inicialização confirma que o contexto falha sem `ADMIN_API_TOKEN`.

- [x] **T020** [C] Criar os DTOs `CadastrarConhecimentoRequest`, `ConhecimentoResponse`,
  `ConhecimentoResumo` e `PaginaConhecimentos` (records) com Bean Validation: título até 200,
  conteúdo até 100.000 e não em branco, categoria obrigatória, fonte de 1 a 500, e tags com no
  máximo 10 itens de até 50 caracteres.
  Dep.: T017
  Verif.: o `CadastrarConhecimentoRequestTest` (com `Validator`) cobre cada limite e cada campo
  obrigatório.

- [x] **T021** [C] Criar o `ConsultarConhecimentoService` e os endpoints
  `GET /api/conhecimentos` (`pagina ≥ 0`, `tamanho` de 1 a 100, `categoria`) e
  `GET /api/conhecimentos/{id}` (400 para UUID inválido, 404 para inexistente).
  Dep.: T018, T019, T020
  Verif.: o `ConhecimentoControllerTest` (`@WebMvcTest`) e o `ConhecimentoApiIT` cobrem CA01.7 e
  CA01.8.

- [x] **T022** [C] Criar o `CadastrarConhecimentoService` (`@Transactional`, ainda sem chunks) e o
  `POST /api/conhecimentos`, que devolve `201` com `Location`, ou `400` ou `415`.
  Dep.: T021
  Verif.: o `ConhecimentoControllerTest` cobre CA01.2–CA01.4, com o `401` antes do `400`; o
  `ConhecimentoApiIT` confirma o registro persistido. **Pendência registrada:** `quantidadeChunks`
  só fica completo na T029.

## Fase 4 — Chunking e geração de embeddings

- [x] **T023** [C] Criar a migração `V3__vector_store.sql`, com a tabela `vector_store`
  (`id uuid`, `content`, `metadata json`, `embedding vector(1536)`) e o índice HNSW
  `vector_cosine_ops`. **Antes**, confira o DDL esperado pelo `PgVectorStore` 2.0.1 no Context7.
  Dep.: T016
  Verif.: o `MigracoesIT` confirma a tabela, a dimensão 1536 e o índice HNSW.

- [x] **T024** [C] Adicionar `spring-ai-starter-model-openai` e
  `spring-ai-starter-vector-store-pgvector`, e configurar:
  - `spring.ai.model.chat=none`;
  - `text-embedding-3-small`;
  - pgvector com `initialize-schema=false`, 1536 dimensões, `COSINE_DISTANCE` e `HNSW`;
  - retry.

  Criar o `test/support/FakeEmbeddingModel` (`@Primary`, determinístico, 1536 dimensões
  normalizadas) e o perfil de teste com `api-key=test-key`.
  Dep.: T023
  Verif.: `./mvnw verify` sobe sem chave real; o teste confirma que não existe bean de
  `ChatModel`.

- [x] **T025** [C] Criar o `ChunkingProperties` (record com `@Validated`: tamanho 800 e
  sobreposição 100, com `sobreposicao < tamanho`).
  Dep.: T024
  Verif.: o `ChunkingPropertiesTest` confirma que, com `sobreposicao ≥ tamanho`, o contexto não
  inicia (CA02.4).

- [x] **T026** [C] Criar o domínio `Chunk` (record com texto, `chunkIndex` e `totalChunks`) e a
  porta `Chunker`.
  Dep.: T017
  Verif.: `./mvnw compile`.

- [x] **T027** [C] Criar o `TokenWindowChunker`:
  - usa o `jtokkit` com `cl100k_base`;
  - aplica uma janela de 800 tokens com passo de 700;
  - ajusta as fronteiras para não cortar caracteres multibyte nem palavras.

  Se o `jtokkit` não vier de forma transitiva, declare-o explicitamente.
  Dep.: T025, T026
  Verif.: o `TokenWindowChunkerTest` cobre:
  - CA02.1 (1 chunk);
  - CA02.2 (≥ 3 chunks para cerca de 2.000 tokens, com sobreposição);
  - RN02.2 (reconstrução sem perda);
  - texto com acentos e emoji.

- [x] **T028** [C] Criar a porta `ChunkStore` e o `PgVectorChunkStore.adicionar(...)`, que grava
  os chunks com `metadata` (`documentoId`, `titulo`, `fonte`, `categoria`, `tags`, `chunkIndex`,
  `totalChunks`, `criadoEm`) via `VectorStore.add`.
  Dep.: T024, T026
  Verif.: o `PgVectorChunkStoreIT` cobre CA02.3 e CA03.1: N linhas com vetor de 1536 dimensões e
  metadados completos.

- [x] **T029** [C] Integrar o `CadastrarConhecimentoService` ao chunking e aos embeddings, dentro de
  `@Transactional`: dividir → inserir `conhecimento` com `quantidadeChunks` → `chunkStore.adicionar`.
  Dep.: T022, T027, T028
  Verif.: o `CadastroAtomicoIT` cobre CA01.1 (`201`, com `quantidadeChunks ≥ 1`, buscável) e
  CA03.2 (`FakeEmbeddingModel` falhando no segundo lote → zero linhas nas duas tabelas).

- [x] **T030** [C] Mapear as falhas de dependência: `DependenciaIndisponivelException` vira
  `503 EMBEDDING_INDISPONIVEL` ou `503 BANCO_INDISPONIVEL`, com `Retry-After: 5`. Configurar o
  timeout de embeddings (`OPENAI_EMBEDDING_TIMEOUT`), conferindo a propriedade no Context7.
  Dep.: T029
  Verif.: o teste cobre CA01.6 (`503`, nada persistido, sem detalhes do provedor no corpo).

## Fase 5 — Busca vetorial e MCP Server

- [x] **T031** [C] Criar o `RagProperties` (limiar 0,70, `topK` padrão 5 e máximo 10) e o domínio
  `ConsultaBusca` (record que valida `pergunta` de 1 a 1.000 após `trim` e `limite` de 1 a 10,
  com padrão 5).
  Dep.: T024
  Verif.: o `ConsultaBuscaTest` cobre os limites, o padrão e a pergunta em branco.

- [x] **T032** [C] Criar o domínio `Trecho` e o `PgVectorChunkStore.buscar(...)`, com
  `SearchRequest.builder().query().topK().similarityThreshold().build()` e a conversão
  `Document → Trecho` (`similaridade` de `getScore()`, com 4 casas).
  Dep.: T028, T031
  Verif.: o `BuscaSemanticaIT` (com `FakeEmbeddingModel`) cobre:
  - CA04.1 (consulta igual a um chunk volta com score alto);
  - CA04.2 (limite e ordenação);
  - CA04.3 (consulta sem relação volta vazia);
  - a confirmação de que o score é `1 − distância de cosseno`.

- [x] **T033** [C] Criar o `BuscaSemanticaService`, que valida, delega e ordena de forma
  decrescente.
  Dep.: T032
  Verif.: o `BuscaSemanticaServiceTest` cobre CA04.4: entrada inválida → exceção, e
  `ChunkStore`/`EmbeddingModel` não são chamados.

- [x] **T034** [C] Criar os DTOs `BuscaSemanticaRequest`, `BuscaSemanticaResponse` e
  `TrechoConhecimento`, e o `POST /api/conhecimentos/busca` (com token e `limiarSimilaridade` na
  resposta).
  Dep.: T019, T033
  Verif.: o `BuscaDiagnosticoControllerTest` cobre 200, 400 e 401; o IT confirma o mesmo resultado
  da busca do serviço.

- [x] **T035** [C] Adicionar `spring-ai-starter-mcp-server-webmvc` e configurar
  `protocol: STREAMABLE`, `/mcp`, `name`, `version`, `type: SYNC` e
  `springdoc.paths-to-exclude=/mcp/**`.
  Dep.: T033
  Verif.: um IT com cliente MCP de teste confirma que o `initialize` em
  `http://localhost:{porta}/mcp` funciona; o `/v3/api-docs` não contém `/mcp`.

- [x] **T036** [C] Criar o `BuscarConhecimentoTool`, com:
  - `@McpTool(name, title, description do contrato, generateOutputSchema = true, annotations
    readOnly/idempotent)`;
  - `@McpToolParam` para `pergunta` e `limite`;
  - `McpMeta` para levar o `correlationId` ao MDC;
  - retorno com os records `ResultadoBuscaMcp` e `TrechoMcp`.

  Dep.: T035
  Verif.: o `BuscarConhecimentoToolIT` cobre CA05.2 (`structuredContent` com trechos e
  `content[0]` equivalente) e CA05.3 (lista vazia, `isError=false`).

- [x] **T037** [C] Criar os erros da ferramenta: `ARGUMENTO_INVALIDO` (sem gerar embedding),
  `EMBEDDING_INDISPONIVEL`, `BANCO_INDISPONIVEL` e `ERRO_INTERNO`, todos com `isError=true` e
  `structuredContent.erro`.
  Dep.: T036
  Verif.: o `BuscarConhecimentoToolIT` cobre CA05.4 e CA05.5 (banco parado, ou `ChunkStore`
  falhando, gera mensagem genérica sem stack trace).

- [x] **T038** [C] Criar o teste de contrato do `tools/list`, comparando com a seção 4.2 do
  contrato: `name`, `title`, `description`, `inputSchema` (`required`, `minLength`, `maxLength`,
  `minimum`, `maximum`, `default`, `additionalProperties`), `outputSchema` e `annotations`. Se
  houver divergência, registrar a ferramenta de forma programática e documentar em
  `03-architecture.md`, seção 5.5.
  Dep.: T037
  Verif.: o `McpToolContractIT` passa (CA05.1).

- [x] **T039** [C] Criar o teste do health da `api-conhecimento`: componente `db` visível, sem
  detalhes, com 200 quando está `UP`.
  Dep.: T014
  Verif.: o `HealthIT` passa; `./mvnw verify` completo passa.

## Fase 6 — MCP Client e API de Atendimentos

- [x] **T040** [A] Criar o domínio `Pergunta`, `Evidencia`, `Resposta`, `Fonte` e `Desfecho`
  (records e enum), a porta `BuscadorDeConhecimento` e a `ConhecimentoIndisponivelException`.
  Dep.: T013
  Verif.: `./mvnw compile`.

- [x] **T041** [A] Criar os DTOs `PerguntaRequest` (`@NotBlank`, `@Size(max = 1000)`),
  `RespostaAtendimentoResponse` e `FonteResposta` (records).
  Dep.: T040
  Verif.: o `PerguntaRequestTest` cobre o branco, a ausência e os 1.001 caracteres.

- [x] **T042** [A] Adicionar `spring-ai-starter-mcp-client` e configurar:
  - `type: SYNC`;
  - `request-timeout: ${MCP_REQUEST_TIMEOUT:10s}`;
  - `initialized: false`;
  - `toolcallback.enabled: false`;
  - a conexão `conhecimento` com `${MCP_CONHECIMENTO_URL}`.

  Criar o `AtendimentoProperties` e o `RagProperties` (`topK`, `contexto-max-caracteres`,
  `mensagem-fallback`, URL obrigatória).
  Dep.: T040, T008
  Verif.: o contexto sobe **sem** MCP Server disponível; o contexto falha sem
  `MCP_CONHECIMENTO_URL`; o enforcer continua passando.

- [x] **T043** [A] Criar o `test/support/FakeMcpServerApplication`: um MCP Server de teste
  Streamable HTTP em porta aleatória, com `buscar_conhecimento` programável (trechos, vazio,
  `isError`, atraso controlado, esquema inválido). O `spring-ai-starter-mcp-server-webmvc` entra
  só com escopo `test`.
  Dep.: T042
  Verif.: um autoteste confirma que o servidor falso aparece no `tools/list`; o enforcer e o
  `dependency:tree` não mostram o starter de server no escopo `compile`.

- [x] **T044** [A] Criar o `McpBuscadorDeConhecimento`:
  - `initialize()` preguiçoso e seguro entre threads;
  - `listTools()` com cache, para confirmar a ferramenta;
  - `callTool` com `limite = topK` e `_meta.correlationId`;
  - leitura de `structuredContent`, com `content[0]` como alternativa;
  - validação dos campos obrigatórios;
  - qualquer falha vira `ConhecimentoIndisponivelException`.

  Dep.: T043
  Verif.: o `McpBuscadorDeConhecimentoIT` cobre:
  - CA07.1 (uma chamada, com os argumentos e o `_meta` certos);
  - lista vazia;
  - CA07.3 (`isError`);
  - CA07.2 (timeout);
  - conexão recusada;
  - esquema inválido;
  - ferramenta ausente.

- [x] **T045** [A] Criar o `ResponderPerguntaService` em versão parcial:
  - falha do MCP → propaga a exceção;
  - lista vazia → fallback (`respondida=false`, `fallback=true`, `fontes=[]`);
  - **com evidência → fallback provisório seguro**, que será substituído na T053.

  Dep.: T044
  Verif.: o `ResponderPerguntaServiceTest` cobre os três caminhos.

- [x] **T046** [A] Criar o `AtendimentoController` com `POST /api/atendimentos/perguntar`, e
  mapear a `ConhecimentoIndisponivelException` para `503 CONHECIMENTO_INDISPONIVEL` com
  `Retry-After: 5`.
  Dep.: T041, T045
  Verif.: o `AtendimentoControllerTest` cobre CA06.1, CA06.2, CA06.3, CA06.4 (cabeçalho devolvido)
  e o `503` com corpo do contrato; no `400`, o serviço não é chamado.

- [x] **T047** [A] Criar o `McpConhecimentoHealthIndicator` (`mcpConhecimento`), que faz
  ping/`listTools`, com timeout de 2 s e cache de 10 s, e confirma que a ferramenta existe.
  Dep.: T044
  Verif.: o `McpHealthIndicatorIT` cobre `UP` com o servidor falso ativo e `DOWN`/503 sem ele;
  `./mvnw verify` passa.

## Fase 7 — Integração com OpenAI e montagem do contexto RAG

- [x] **T048** [A] Criar o domínio `ContextoRag`, `TrechoContexto` e `SaidaModelo` (records) e o
  `MontadorDeContexto`, conforme a seção 6.2 e a 6.3 do contrato:
  - descarta itens incompletos;
  - deduplica por `chunkId`;
  - ordena por similaridade, com nulos por último;
  - escapa `<` e `>`;
  - inclui trechos inteiros até 12.000 caracteres, sem truncar e pulando os que não cabem;
  - atribui ids `T1…Tn`;
  - devolve vazio quando nada cabe.

  Dep.: T040
  Verif.: o `MontadorDeContextoTest` cobre cada regra, incluindo CA08.2 (`</contexto>` no trecho
  é escapado).

- [x] **T049** [A] Criar o `DerivadorDeFontes`:
  - mapeia `Tn` para `Evidencia`;
  - descarta ids inexistentes, contando os descartes;
  - deduplica e mantém a ordem do contexto;
  - monta a `Fonte` só a partir da evidência;
  - sinaliza o fallback quando `respondida=false` ou quando não sobra fonte válida.

  Dep.: T048
  Verif.: o `DerivadorDeFontesTest` cobre CA08.3 e CA09.2.

- [x] **T050** [A] Criar o `prompts/sistema.st`, com as 7 regras da seção 6.3 do contrato, e o
  `prompts/usuario.st` (contexto e pergunta como parâmetros).
  Dep.: T048
  Verif.: o `PromptsTest` confirma que o sistema contém cada regra obrigatória e que o template
  renderizado contém os delimitadores e os ids `Tn`.

- [x] **T051** [A] Adicionar `spring-ai-starter-model-openai` e configurar
  `spring.ai.model.embedding=none`, `OPENAI_CHAT_MODEL`, temperatura 0, retry e
  `OPENAI_CHAT_TIMEOUT` (propriedade conferida no Context7).
  Dep.: T042
  Verif.: o contexto sobe com `test-key`; o teste confirma que **não** existe bean de
  `EmbeddingModel`; o enforcer passa.

- [x] **T052** [A] Criar a porta `GeradorDeResposta`, o `ChatClientConfig` (`defaultSystem`,
  **sem** `defaultTools`) e o `ChatClientGeradorDeResposta` (`entity(SaidaModelo.class)`, com as
  exceções do Spring AI convertidas em `ModeloIndisponivelException`).
  Dep.: T049, T050, T051
  Verif.: o `ChatClientGeradorDeRespostaTest` (com `StubChatModel`) cobre:
  - sucesso;
  - falha transitória com as tentativas esgotadas;
  - falha não transitória;
  - JSON inválido (CA10.5 e CA10.6);
  - nenhuma ferramenta presente no prompt enviado.

- [x] **T053** [A] Completar o `ResponderPerguntaService` com o fluxo da seção 6.1 do contrato,
  removendo o fallback provisório da T045, e mapear `ModeloIndisponivelException` para
  `503 MODELO_INDISPONIVEL`.
  Dep.: T045, T052
  Verif.: o `ResponderPerguntaServiceTest` cobre:
  - CA08.1, CA08.4 e CA08.5;
  - CA10.1 e CA10.2 (`verify(chatModel, never())`);
  - CA10.3;
  - todos os desfechos.

- [x] **T054** [A] Criar o IT ponta a ponta `AtendimentoFluxoIT`, com `FakeMcpServerApplication` e
  `StubChatModel`.
  Dep.: T046, T047, T053
  Verif.: cobre a resposta fundamentada com as fontes da seção 5.1 (CA09.1), o fallback (CA09.3)
  e os dois `503`; `./mvnw verify` na raiz passa.

- [ ] **T055** [R] Validação manual com a OpenAI real, fora do `verify`:
  1. subir o PostgreSQL e os dois serviços;
  2. cadastrar 3 documentos;
  3. fazer uma pergunta respondível, uma sem evidência e uma adversarial.

  Dep.: T030, T038, T054
  Verif.: o relatório mostra os comandos e as respostas reais (sem conteúdo sensível nos logs).
  Se não houver chave disponível, a tarefa fica "não executada" com o motivo.
  **Status (2026-10-25, 2ª execução): 🟥 EXECUTADA COM FALHA NO CASO ADVERSARIAL.** Com a OpenAI real e
  as duas APIs, as perguntas respondíveis, as sem evidência e o pedido do prompt se comportaram como
  esperado. Porém "Os cursos da COTI são gratuitos?" foi respondida com "são gratuitos", citando o
  documento que contém a instrução injetada (viola os Art. 10 e 12). A tarefa segue aberta até a
  mitigação. Ver `08-checklist.md`, Fase 8, T055/T064.
  **Status anterior: ⛔ BLOQUEADA, sem `OPENAI_API_KEY` no `.env`.** Um smoke parcial foi
  executado com as duas APIs reais e uma chave inválida: health `mcpConhecimento` UP via MCP real;
  pergunta → 503 `CONHECIMENTO_INDISPONIVEL`; cadastro → 503 `EMBEDDING_INDISPONIVEL` sem gravar;
  `correlationId` nos dois serviços (ver `08-checklist.md`, Fase 7). Para concluir, defina
  `OPENAI_API_KEY` e `ADMIN_API_TOKEN` no `.env` e repita os passos 1 a 3.

## Fase 8 — Testes, segurança e observabilidade

- [x] **T056** [C] Adicionar o ArchUnit e criar o `CamadasArchTest`: o domínio não depende de web,
  JDBC, MCP SDK nem Jackson, e o controller não depende de infraestrutura.
  Dep.: T039
  Verif.: o teste passa; uma violação proposital faz o teste falhar (reverter em seguida).

- [x] **T057** [A] Adicionar o ArchUnit e criar o `CamadasArchTest` e o `ProibicoesArchTest`
  (nenhuma classe usa `org.springframework.jdbc`, `javax.sql`,
  `org.springframework.ai.vectorstore` nem `org.springframework.ai.embedding`).
  Dep.: T054
  Verif.: os testes passam.

- [x] **T058** [A] Criar as métricas `coti.atendimento.desfecho{tipo}`,
  `coti.atendimento.fontes.descartadas`, `coti.mcp.chamada{resultado}` e os timers por etapa
  (MCP, modelo, total).
  Dep.: T054
  Verif.: o teste com `SimpleMeterRegistry` confirma os incrementos por desfecho; as métricas
  aparecem em `/actuator/metrics`.

- [x] **T059** [R] Garantir logs seguros nos dois serviços:
  - log de metadados por etapa;
  - o `LogConteudoGuard` (`coti.log.conteudo=true` só com o perfil `dev`, senão a aplicação não
    inicia);
  - o `LogSeguroTest`, que usa `OutputCaptureExtension` e marcadores únicos.
  **Decisão (2026-10-25):** o `LogConteudoGuard` não foi criado, porque nenhum código registra conteúdo em
  nenhum perfil e não há o que proteger (regra 3). Fica registrado em `03-architecture.md`, seção 7.

  Dep.: T054, T039
  Verif.: os logs não contêm a pergunta, a resposta, o conteúdo, o token nem a chave (CA06.5 e
  CA01.5); o guard bloqueia a combinação inválida.

- [x] **T060** [R] Criar o `CorrelacaoPontaAPontaIT`: na `api-atendimentos`, o servidor falso
  recebe `_meta.correlationId` igual ao cabeçalho enviado; na `api-conhecimento`, o log da
  ferramenta contém o `correlationId` recebido no `_meta`.
  Dep.: T036, T054
  Verif.: os ITs dos dois serviços passam (CA06.4).

- [x] **T061** [R] Anotar os controllers e os DTOs com OpenAPI (`@Operation`, `@ApiResponse`,
  `@Schema` com os exemplos do contrato) e criar o `OpenApiContractIT` nos dois serviços.
  Dep.: T034, T054
  Verif.: o `/v3/api-docs` contém exatamente os endpoints das seções 3 e 5 do contrato, os
  códigos de resposta e os exemplos, e não contém `/mcp`.

- [x] **T062** [A] Criar a `SuiteAdversarialTest`, com fixtures em
  `test/resources/adversarial/`: ordem para ignorar as regras, `</contexto>` seguido de
  instruções, pedido do prompt de sistema e preço de curso inexistente.
  Dep.: T053
  Verif.: a suíte confirma:
  - o escape e o isolamento no prompt capturado;
  - as regras presentes no prompt de sistema;
  - o fallback sem chamar o modelo quando não há evidência;
  - a resposta da API sem o texto do prompt de sistema.

- [x] **T063** [R] Verificar a segurança:
  - o `ErroSemVazamentoTest` cobre CA10.7 nos dois serviços;
  - o limite de corpo é compatível com 100.000 caracteres;
  - a exposição do Actuator fica restrita;
  - a varredura de segredos roda com `gitleaks detect`, se disponível, ou com `git grep`.

  Dep.: T059, T061
  Verif.: os testes passam; a saída da varredura vai para o relatório.

- [x] **T064** [C] Calibrar o limiar de similaridade (ADR-014) com cerca de 20 perguntas de
  referência, usando o endpoint de diagnóstico e a OpenAI real, e registrar o valor final e a
  justificativa em `03-architecture.md`, ADR-014.
  Dep.: T034, T055
  Verif.: a tabela pergunta → melhor similaridade → decisão aparece no relatório; o
  `RAG_SIMILARITY_THRESHOLD` padrão é atualizado se necessário. Sem chave, a tarefa fica
  "bloqueada".
  **Status (2026-10-25): ✅ concluída.** Foram usados 5 documentos e 21 perguntas com a OpenAI real.
  Relevantes: 0,399 a 0,802 (sempre com o documento certo em 1º lugar). Irrelevantes: 0,114 a 0,371.
  O limiar padrão passou de 0,70 para **0,38** (com 0,70, 10 das 13 relevantes cairiam em fallback).
  Registrado na ADR-014.

- [ ] **T065** [R] Fazer a revisão final contra a constituição e o `specs/08-checklist.md`,
  atualizar a matriz de `specs/07-test-plan.md` e rodar `./mvnw verify` na raiz.
  Dep.: T056–T064
  Verif.: o checklist está completo e o relatório final mostra a saída real do build e dos testes.
  **Status (2026-10-25): executada parcialmente.** A revisão contra a constituição e o checklist, a matriz
  em `07-test-plan.md` e o `./mvnw verify` da raiz (224 testes) foram feitos. Falta só reexecutá-la depois
  da T055 e da T064, das quais depende.

---

## Primeira implementação

Executam-se **as Fases 1 e 2 completas: T001 a T013.** Elas não dependem de nada fora delas e
entregam a base exigida por todas as fases seguintes.

| Bloco | Tarefas | Pré-condição | Paralelismo |
|---|---|---|---|
| Infraestrutura | T001 → T002 → T003 | Docker Desktop ativo | Independente do bloco Maven |
| `api-conhecimento` | T004 → T005 → T010 → T011 | Java 21 | Em paralelo com o bloco `api-atendimentos` |
| `api-atendimentos` | T006 → T007 → T008, T012 → T013 | Java 21 | Em paralelo com o bloco `api-conhecimento` |
| Agregador | T009 | T004 e T006 | Depois dos dois scaffolds |

**Não** entram na primeira implementação:
- **T014 em diante.** A T014 exige a T003 (PostgreSQL ativo) e a T011, e abre a Fase 3. Toda a
  Fase 3 depende das Fases 1 e 2 concluídas e verificadas.
- **Fases 6 e 7 na `api-atendimentos`.** Mesmo que a estrutura do serviço fique pronta na Fase 2,
  elas dependem do contrato MCP implementado e testado na Fase 5 (T038).

**Antes da Fase 3**, deixe prontos os pré-requisitos documentais da seção 2.1 do `04-plan.md`:
`08-checklist.md`, `07-test-plan.md` e o alinhamento das skills.
