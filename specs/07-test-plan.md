# Plano de Testes — COTI AI Atendimento (MVP)

> Estratégia de testes e matriz de rastreabilidade **critério de aceite → teste**
> ([`02-requirements.md`](02-requirements.md)). Constituição: Art. 14. Skill: `skills/testing/`.
> Estado em 2026-10-25: **224 testes automatizados**, todos aprovados no `./mvnw verify` da raiz.

## 1. Estratégia

| Nível | Ferramentas | Onde | Executa com |
|---|---|---|---|
| Unitário | JUnit 5, Mockito, AssertJ | Domínio, serviços, prompts, filtros, propriedades | Surefire (`*Test`) |
| Fatia web | `@WebMvcTest` + `MockMvcTester` | Controllers, validação, formato de erro | Surefire |
| Arquitetura | ArchUnit 1.5.1 | Camadas e proibições (ADR-013) | Surefire (`*ArchTest`) |
| Integração `api-conhecimento` | `@SpringBootTest` + Testcontainers `pgvector/pgvector:pg16` + `FakeEmbeddingModel` | Migrações, ingestão, busca, MCP Server | Failsafe (`*IT`), **exige Docker** |
| Integração `api-atendimentos` | `@SpringBootTest` + `FakeMcpServer` (MCP real em porta aleatória) + `StubChatModel` | Cliente MCP, fluxo B, health | Failsafe (`*IT`) |
| Contrato | Cliente MCP do SDK oficial; `/v3/api-docs` | Seções 3, 4 e 5 do contrato | Failsafe/Surefire |
| Adversarial | Componentes reais + modelo simulado que "obedece" à injeção | Prompt injection e dados inventados | Surefire |
| Manual | Duas APIs reais + OpenAI real | Comportamento real do modelo e calibração | Fora do `verify` (T055, T064) |

**Regras:**
- Nenhum teste chama a OpenAI real (`spring.ai.openai.api-key=test-key`).
- O `FakeEmbeddingModel` usa "feature hashing" lexical. A similaridade é previsível: texto idêntico
  dá 1,0, e 9 de 12 palavras em comum dá cerca de 0,75.
- Estado compartilhado entre classes de teste (modelo falso, servidor falso) é reiniciado antes e
  depois de cada teste.

**Comandos:**
- `docker info`
- `./mvnw verify` (raiz; os dois serviços)
- `cd api-conhecimento && ./mvnw verify`
- `cd api-atendimentos && ./mvnw verify`

## 2. Matriz de rastreabilidade — requisitos funcionais

Legenda: `[C]` = `api-conhecimento`, `[A]` = `api-atendimentos`.

| CA | Descrição resumida | Teste(s) |
|---|---|---|
| CA01.1 | Cadastro válido → 201, chunks pesquisáveis | `[C] CadastroAtomicoIT.cadastroGravaDocumentoEChunksComEmbeddings`, `ConhecimentoApiIT.cadastroPersisteEPodeSerConsultadoPorId`, `ConhecimentoControllerTest.cadastroValidoGera201ComLocation` |
| CA01.2 | Sem `conteudo` → 400, nada persistido | `[C] ConhecimentoControllerTest.semConteudoGera400ESemChamarOServico`, `CadastrarConhecimentoRequestTest.camposObrigatorios` |
| CA01.3 | Categoria inválida → 400 no campo | `[C] ConhecimentoControllerTest.categoriaInvalidaGera400NoCampoCategoria` |
| CA01.4 | Conteúdo > 100.000 → 400 | `[C] ConhecimentoControllerTest.conteudoAcimaDoLimiteGera400`, `CadastrarConhecimentoRequestTest.limitesDeTamanho`; limite aceito: `ConhecimentoApiIT.conteudoNoLimiteDe100MilCaracteresEAceito` |
| CA01.5 | Sem token ou token inválido → 401, sem log do valor | `[C] AdminTokenFilterTest` (5), `ConhecimentoControllerTest.semTokenGera401AntesDaValidacao`, `AdminPropertiesTest` (3) |
| CA01.6 | Falha de embeddings → 503, nada persistido | `[C] CadastroAtomicoIT.falhaDoProvedorGera503SemDetalhesInternos`, `ConhecimentoControllerTest.falhaDeDependenciaGera503ComRetryAfter` |
| CA01.7 | Listagem paginada e filtrada | `[C] ConhecimentoApiIT.listagemPaginadaEFiltradaSemConteudo`, `JdbcConhecimentoRepositoryIT` |
| CA01.8 | Consulta por id; 404 | `[C] ConhecimentoApiIT`, `ConhecimentoControllerTest.consultaPorIdInexistenteGera404` |
| CA02.1 | Documento curto → 1 chunk | `[C] TokenWindowChunkerTest.documentoCurtoGeraUmChunk` |
| CA02.2 | Documento longo → chunks com sobreposição | `[C] TokenWindowChunkerTest.documentoLongoGeraChunksSequenciaisComSobreposicao` |
| CA02.3 | Metadados propagados | `[C] PgVectorChunkStoreIT.gravaUmRegistroPorChunkComVetorEMetadados` |
| CA02.4 | Sobreposição ≥ tamanho impede a inicialização | `[C] ChunkingPropertiesTest` (3) |
| RN02.2 | Reconstrução sem perda | `[C] TokenWindowChunkerTest.reconstrucaoSemPerdaComAcentosEEmoji`, `palavraGiganteSemEspacosEPartidaSemPerda` |
| CA03.1 | N registros com vetor de 1536 dimensões | `[C] PgVectorChunkStoreIT`, `CadastroAtomicoIT` |
| CA03.2 | Falha no 2º lote → rollback total | `[C] CadastroAtomicoIT.falhaNoSegundoLoteDeEmbeddingsDesfazTudo` |
| CA03.3 | Mesmo `EmbeddingModel` na ingestão e na busca | `[C] BuscaSemanticaIT.ingestaoEBuscaUsamOMesmoModeloDeEmbedding`, `ApiConhecimentoApplicationIT` |
| CA03.4 | Migrações do zero | `[C] MigracoesIT` (5) |
| CA04.1 | Trechos relevantes com metadados | `[C] BuscaSemanticaIT.trechosRelevantesComMetadadosESimilaridade` |
| CA04.2 | Ordem e limite | `[C] BuscaSemanticaIT.respeitaOLimiteEAOrdemDecrescente` |
| CA04.3 | Sem evidência → lista vazia | `[C] BuscaSemanticaIT.perguntaSemRelacaoRetornaListaVazia`, `nadaAbaixoDoLimiarEDevolvido` |
| CA04.4 | Entrada inválida sem embedding | `[C] ConsultaBuscaTest`, `BuscaDiagnosticoIT.entradaInvalidaNaoGeraEmbedding` |
| CA05.1 | `tools/list` igual ao contrato | `[C] McpToolContractIT` (lê o JSON direto de `06-api-contracts.md`) |
| CA05.2 | Execução com resultados estruturados | `[C] BuscarConhecimentoToolIT.execucaoComResultadosEstruturados` |
| CA05.3 | Execução sem evidência (não é erro) | `[C] BuscarConhecimentoToolIT.semEvidenciaNaoEErro` |
| CA05.4 | Argumento inválido → `ARGUMENTO_INVALIDO`, sem embedding | `[C] BuscarConhecimentoToolIT.argumentosInvalidosNaoGeramEmbedding` |
| CA05.5 | Banco ou provedor indisponível → erro genérico | `[C] BuscarConhecimentoToolTest` (4), `BuscarConhecimentoToolIT.falhaDoProvedorDeEmbeddingsViraErroGenerico` |
| CA06.1 | Pergunta válida → 200 | `[A] AtendimentoControllerTest.perguntaValidaGera200NoFormatoDoContrato`, `AtendimentoFluxoIT` |
| CA06.2 | Pergunta vazia → 400, sem MCP nem modelo | `[A] AtendimentoControllerTest.perguntaAusenteOuEmBrancoGera400SemChamarOServico`, `PerguntaRequestTest` |
| CA06.3 | Pergunta > 1.000 → 400 | `[A] AtendimentoControllerTest.perguntaLongaDemaisGera400` |
| CA06.4 | Correlação devolvida e nos dois serviços | `[A] AtendimentoControllerTest`, `AtendimentoFluxoIT` (`_meta.correlationId`), `[C] BuscarConhecimentoToolIT.correlationIdDoMetaVaiParaOsLogs` |
| CA06.5 | Logs sem a pergunta nem a resposta | `[A] AtendimentoFluxoIT.logsTemSoMetadadosEOCorrelationId`, `[C] LogSeguroIT` |
| CA07.1 | Uma chamada MCP com os argumentos certos | `[A] McpBuscadorDeConhecimentoIT.chamaAFerramentaUmaVezComArgumentosEMeta` |
| CA07.2 | Timeout | `[A] McpBuscadorDeConhecimentoIT.timeoutGeraIndisponivel` |
| CA07.3 | `isError` → indisponibilidade | `[A] McpBuscadorDeConhecimentoIT.erroDaFerramentaGeraIndisponivel` |
| CA07.4 | Sem banco na `api-atendimentos` | `[A] ProibicoesArchTest`, `ApiAtendimentosApplicationTests`, `maven-enforcer-plugin` (`BannedDependencies`) |
| CA08.1 | Resposta fundamentada, fontes do contexto | `[A] ResponderPerguntaServiceTest`, `AtendimentoFluxoIT.respostaFundamentadaComFontesNoFormatoDoContrato` |
| CA08.2 | Contexto isolado (delimitadores escapados) | `[A] MontadorDeContextoTest.delimitadoresNoConteudoSaoEscapados`, `ChatClientGeradorDeRespostaTest`, `SuiteAdversarialTest.fechamentoDeDelimitadorNaoAbreNovoBlocoNoPrompt` |
| CA08.3 | Fonte inventada descartada | `[A] DerivadorDeFontesTest`, `ResponderPerguntaServiceTest`, `AtendimentoFluxoIT.fonteInventadaPeloModeloViraFallback` |
| CA08.4 | Modelo declara que não sabe → fallback | `[A] DerivadorDeFontesTest`, `ResponderPerguntaServiceTest.modeloDeclaraQueNaoSabeViraFallback` |
| CA08.5 | Preço ausente → sem valores | `[A] VerificadorDeRespostaTest`, `ResponderPerguntaServiceTest.precoInventadoComFonteValidaViraFallback`, `SuiteAdversarialTest.precoDeCursoInexistenteNaBaseNaoEInventado` |
| RN08.4 / RN08.6 | Prompt de sistema nunca devolvido; valores verificados | `[A] VerificadorDeRespostaTest`, `SuiteAdversarialTest.pedidoDoPromptDeSistemaNuncaVoltaAoAluno`, `precoQueConstaNaFonteEDevolvido` |
| CA09.1 | Fontes presentes com metadados da base | `[A] AtendimentoFluxoIT.respostaFundamentadaComFontesNoFormatoDoContrato`, `DerivadorDeFontesTest` |
| CA09.2 | Só as fontes usadas | `[A] AtendimentoFluxoIT.somenteAsFontesUsadasPeloModeloSaoDevolvidas`, `DerivadorDeFontesTest` |
| CA09.3 | Fallback sem fontes | `[A] AtendimentoFluxoIT.semEvidenciaFallbackSemChamarOModelo` |
| CA10.1 | Sem evidência → fallback, modelo não chamado | `[A] ResponderPerguntaServiceTest.semEvidenciaFallbackSemChamarOModelo`, `AtendimentoFluxoIT`, `SuiteAdversarialTest.semEvidenciaOModeloNemEChamado` |
| CA10.2 | Curso inexistente → fallback sem horário | `[A] SuiteAdversarialTest.modeloQueObedeceAInjecaoComHorarioInventadoCaiNoFallback`, `VerificadorDeRespostaTest` |
| CA10.3 | MCP fora do ar → 503 sem chamar o modelo | `[A] McpServidorForaDoArIT`, `AtendimentoFluxoIT.falhaDoMcpGera503SemChamarOModelo`, `ResponderPerguntaServiceTest` |
| CA10.4 | Timeout do MCP → 503 dentro do limite | `[A] McpBuscadorDeConhecimentoIT.timeoutGeraIndisponivel` (< 4 s com timeout de 2 s) |
| CA10.5 | Falha da OpenAI → 503 | `[A] ChatClientGeradorDeRespostaTest`, `AtendimentoFluxoIT.falhaDoModeloGera503` |
| CA10.6 | Saída estruturada inválida → 503 sem texto livre | `[A] ChatClientGeradorDeRespostaTest.saidaQueNaoEJsonViraModeloIndisponivel`, `AtendimentoFluxoIT.saidaInvalidaDoModeloGera503SemTextoLivre` |
| CA10.7 | Erro sem vazamento | `[A][C] GlobalExceptionHandlerTest.erroInesperadoGera500SemVazarDetalhes`, `AtendimentoFluxoIT.falhaDoModeloGera503`, `CadastroAtomicoIT`, `BuscarConhecimentoToolTest` |

**Lacunas:** nenhuma entre os critérios automatizáveis. O comportamento do **modelo real**
(aderência às regras, qualidade das respostas) depende da T055, e a calibração do limiar, da T064.
As duas ficam fora do `verify` e estão bloqueadas sem `OPENAI_API_KEY`.

## 3. Matriz — requisitos não funcionais

| RNF | Teste(s) / verificação |
|---|---|
| RNF01 Segurança | `AdminTokenFilterTest`, `AdminPropertiesTest`, `OperacaoEndpointsIT/Test.actuatorNaoExpoeEndpointsSensiveis`, `GlobalExceptionHandlerTest`, varredura de segredos (`grep`, T063), bind `127.0.0.1` (execução real) |
| RNF02 Observabilidade | `CorrelationIdFilterTest`, `LogSeguroIT`, `AtendimentoFluxoIT.logsTemSoMetadadosEOCorrelationId` e `.metricasDoAtendimentoExpostasNoActuator`, `MicrometerMetricasAtendimentoTest`, `McpHealthIndicatorIT`, `OperacaoEndpoints*` (health) |
| RNF03 Manutenibilidade | `CamadasArchTest` (×2), `ProibicoesArchTest`, `OpenApiContractIT`, `OpenApiContractTest` |
| RNF04 Timeouts | `McpBuscadorDeConhecimentoIT.timeoutGeraIndisponivel`, `McpConhecimentoHealthIndicatorTest.downQuandoPassaDoTimeoutDe2Segundos`, `ChatClientGeradorDeRespostaTest.timeoutViraModeloIndisponivel` |
| RNF05 Validação de entrada | `CadastrarConhecimentoRequestTest`, `ConsultaBuscaTest`, `PerguntaRequestTest`, `GlobalExceptionHandlerTest`, `BuscarConhecimentoToolIT.argumentosInvalidosNaoGeramEmbedding` |
| RNF06 Testabilidade | Toda a suíte roda sem OpenAI real; Testcontainers; `FakeMcpServer`; esta matriz |
| RNF07 Separação | Enforcer, `ProibicoesArchTest`, `ApiAtendimentosApplicationTests`, `McpServidorForaDoArIT` (sobe sem a outra API) |
| RNF08 Prompt injection | `SuiteAdversarialTest` (7), `MontadorDeContextoTest`, `ChatClientGeradorDeRespostaTest` (sem ferramentas), `VerificadorDeRespostaTest`, `PromptsTest` |
| RNF09 Variáveis de ambiente | `AdminPropertiesTest`, `PropriedadesTest`, `ChunkingPropertiesTest`, `.env.example` (T001) |

## 4. Testes manuais (fora do `verify`)

| Tarefa | Objetivo | Estado |
|---|---|---|
| T055 | Duas APIs reais + OpenAI real: pergunta respondível, sem evidência e adversarial | ⛔ Bloqueada (sem `OPENAI_API_KEY`). Smoke parcial executado com chave inválida (MCP real, 503 corretos, correlação). |
| T064 | Calibração do limiar com cerca de 20 perguntas de referência via `POST /api/conhecimentos/busca` | ⛔ Bloqueada (sem `OPENAI_API_KEY`) |
