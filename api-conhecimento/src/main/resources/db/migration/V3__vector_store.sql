-- Tabela do PgVectorStore (esquema oficial do Spring AI 2.0; initialize-schema=false).
-- Dimensão 1536 = text-embedding-3-small. Trocar o modelo exige nova migração e reindexação (RN03.2).
CREATE TABLE vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

CREATE INDEX idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);
