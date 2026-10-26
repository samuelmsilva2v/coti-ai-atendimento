-- Documento integral cadastrado pelo administrador (RF01; specs/03-architecture.md, seção 5.2).
CREATE TABLE conhecimento (
    id                uuid          PRIMARY KEY,
    titulo            varchar(200)  NOT NULL,
    conteudo          text          NOT NULL,
    categoria         varchar(20)   NOT NULL
        CHECK (categoria IN ('CURSO', 'PROGRAMA', 'TECNOLOGIA', 'MODALIDADE', 'PRE_REQUISITO', 'INSTITUCIONAL')),
    fonte             varchar(500)  NOT NULL,
    tags              text[]        NOT NULL DEFAULT '{}',
    quantidade_chunks integer       NOT NULL DEFAULT 0 CHECK (quantidade_chunks >= 0),
    tamanho_conteudo  integer       NOT NULL CHECK (tamanho_conteudo > 0),
    criado_em         timestamptz   NOT NULL
);

CREATE INDEX idx_conhecimento_categoria_criado_em ON conhecimento (categoria, criado_em DESC);
