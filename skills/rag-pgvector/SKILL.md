---
name: rag-pgvector
description: Orienta o RAG da api-conhecimento com Spring AI 2.0.1 e PostgreSQL/pgvector — ingestão de textos, chunking, embeddings, PgVectorStore, similaritySearch, metadados, fontes, relevância e prevenção de respostas sem evidência. Use ao criar ou alterar ingestão, esquema vetorial, busca semântica ou regras de relevância.
---

# Skill: RAG com PgVectorStore

> Base: Spring AI **2.0.1**, Spring Boot 4.0.x, PostgreSQL com pgvector (imagem
> `pgvector/pgvector`). **Somente na `api-conhecimento`.**
> Constituição: Art. 5, 6, 9, 10, 11, 12, 13.

## Quando usar

- Criar ou alterar a ingestão de documentos na base de conhecimento.
- Definir a estratégia de chunking e os metadados dos chunks.
- Configurar o `PgVectorStore`, o esquema e as migrações.
- Implementar ou ajustar a busca semântica usada pela ferramenta MCP.
- Definir limiar de relevância, `topK` e regras de evidência mínima.

## Responsabilidades

- Transformar documentos autorizados em chunks com metadados de origem.
- Gerar embeddings (o único lugar do sistema que faz isso — Art. 6).
- Persistir e versionar o esquema vetorial.
- Executar a busca e devolver apenas trechos relevantes, com fonte e score.
- Garantir que a `api-atendimentos` nunca receba trechos irrelevantes como "evidência".

## Passo a passo de implementação

1. **Dependências** (apenas na `api-conhecimento`):

   ```xml
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
   </dependency>
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-starter-model-openai</artifactId>   <!-- EmbeddingModel -->
   </dependency>
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-database-postgresql</artifactId>
   </dependency>
   ```

   Use o starter de Flyway do Spring Boot adotado, se exigido pela versão 4.x.

2. **Configuração:**

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT}/${POSTGRES_DB}
       username: ${POSTGRES_USER}
       password: ${POSTGRES_PASSWORD}
     ai:
       model:
         chat: none
       openai:
         api-key: ${OPENAI_API_KEY}
         embedding:
           options:
             model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
       vectorstore:
         pgvector:
           initialize-schema: false     # esquema vem das migrações
           dimensions: 1536             # igual à dimensão do modelo de embedding
           distance-type: COSINE_DISTANCE
           index-type: HNSW
   ```

   Modelo e dimensão são uma decisão única, registrada em `specs/03-architecture.md`.

3. **Migração Flyway** (`db/migration/V1__vector_store.sql`), compatível com o esquema esperado
   pelo `PgVectorStore`:

   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS hstore;
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

   CREATE TABLE IF NOT EXISTS vector_store (
       id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
       content   text,
       metadata  json,
       embedding vector(1536)
   );

   CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
       ON vector_store USING HNSW (embedding vector_cosine_ops);
   ```

   Confira o DDL esperado pela versão 2.0.1 na documentação do PgVector (Context7) antes de criar
   a migração.

4. **Ingestão** (serviço de aplicação, ex.: `IngestaoService`):
   1. receber o texto e seus metadados de origem por um processo aprovado (o meio de entrada —
      endpoint administrativo, carga inicial — é definido em `02-requirements.md` e no contrato);
   2. validar: texto não vazio, `sourceId` e `titulo` obrigatórios;
   3. criar um `Document` com os metadados do documento;
   4. dividir com `TokenTextSplitter` (tamanho e sobreposição em configuração; ponto de partida:
      ~500–800 tokens por chunk);
   5. enriquecer cada chunk com `sourceId`, `titulo`, `chunkIndex`, `atualizadoEm` (e categoria,
      se o requisito pedir);
   6. reingestão do mesmo `sourceId`: remover os chunks antigos
      (`vectorStore.delete(filterExpression)`) antes de adicionar os novos, para não coexistirem
      versões conflitantes de preços ou datas (Art. 12);
   7. `vectorStore.add(chunks)` — o `PgVectorStore` gera os embeddings com o `EmbeddingModel`.

5. **Busca** (serviço de domínio/aplicação, ex.: `BuscaSemanticaService`), usando a API de
   builder do 2.0:

   ```java
   List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
           .query(consulta.pergunta())
           .topK(consulta.limite())
           .similarityThreshold(propriedades.limiarSimilaridade())
           .build());
   ```

   - Converta cada `Document` em `TrechoEncontrado(sourceId, titulo, conteudo, score)` usando
     `getText()`, `getMetadata()` e `getScore()`.
   - `limiarSimilaridade` e `topK` padrão ficam em configuração própria
     (`@ConfigurationProperties` com `record`), calibrados com o golden set.
   - Lista vazia é um resultado válido: a `api-atendimentos` aplica o fallback (Art. 11).

6. **Prevenção de respostas sem evidência.**
   - Descartar resultados abaixo do limiar no próprio `SearchRequest`.
   - Nunca preencher o resultado com "os mais próximos" quando nada passa do limiar.
   - Todo trecho devolvido carrega `sourceId` e `titulo` para citação (Art. 9).

7. **Conteúdo como dado.** Na ingestão, guarde o texto como recebido (sem executar nada que esteja
   nele). Na busca, devolva o conteúdo apenas como campo de dado da resposta da ferramenta; a
   neutralização de delimitadores e o isolamento no prompt ficam na `api-atendimentos`
   (skill `spring-ai`). Documentos com instruções suspeitas podem ser sinalizados em metadados
   para revisão, mas a defesa principal é o isolamento no prompt (Art. 10).

8. **Logs:** registrar `sourceId`, quantidade de chunks, ids e scores retornados e latência;
   nunca o texto completo dos documentos ou das perguntas (Art. 13).

## Padrões de codificação

- `VectorStore` (interface) injetado nos serviços; detalhes do pgvector só na configuração.
- Metadados com chaves constantes em um único lugar (ex.: `MetadadosChunk.SOURCE_ID`).
- Configuração de chunking, limiar e `topK` em `@ConfigurationProperties` (`record`).
- Esquema versionado por Flyway; `initialize-schema: false`.
- `record` para consultas e resultados.

## Antipadrões proibidos

- Qualquer dependência de pgvector, JDBC, JPA ou `VectorStore` na `api-atendimentos` (Art. 5).
- Gerar embeddings fora da `api-conhecimento` ou expor vetores na ferramenta MCP (Art. 6).
- `initialize-schema: true` ou `removeExistingVectorStoreTable(true)` fora de testes.
- Dimensão da coluna diferente da dimensão do modelo de embedding.
- Trocar o modelo de embedding sem reindexar a base.
- API antiga `SearchRequest.query(...).withTopK(...)`: use `SearchRequest.builder()`.
- `similarityThresholdAll()` ou limiar 0 na busca de produção.
- Chunks sem `sourceId`/`titulo` (resposta sem fonte rastreável).
- Manter versões antigas de um documento após reingestão.
- Logar conteúdo completo de documentos ou perguntas.

## Critérios de validação

- Teste de integração (Testcontainers `pgvector/pgvector`) com `EmbeddingModel` simulado e
  determinístico: ingestão gera N chunks com os metadados esperados.
- Teste: a busca devolve os trechos mais próximos, com `score` e fontes.
- Teste: consulta sem trecho acima do limiar devolve lista vazia.
- Teste: reingestão do mesmo `sourceId` substitui os chunks antigos.
- Teste: as migrações sobem do zero em um banco vazio.
- Teste: ingestão e busca usam o mesmo `EmbeddingModel` e a mesma dimensão.

## Checklist de conclusão

- [ ] Starter `spring-ai-starter-vector-store-pgvector` somente na `api-conhecimento`.
- [ ] Migração Flyway com extensões, tabela e índice HNSW; `initialize-schema: false`.
- [ ] Modelo e dimensão de embedding alinhados e registrados na arquitetura.
- [ ] Chunking configurável com metadados `sourceId`, `titulo`, `chunkIndex`, `atualizadoEm`.
- [ ] Reingestão substitui versões antigas.
- [ ] Busca com `SearchRequest.builder()`, `topK` e limiar configurados.
- [ ] Resultados com fonte e score; lista vazia quando não há evidência.
- [ ] Logs só com metadados.
- [ ] Testes com Testcontainers executados nesta interação (Docker Desktop ativo).
