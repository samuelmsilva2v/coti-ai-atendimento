# AGENTS.md — Instruções para agentes de codificação

Este arquivo vale para **todo agente de codificação** (Claude Code, Codex, Copilot, Cursor etc.)
que trabalhar no repositório **COTI AI Atendimento**. Ele é operacional: diz *como* trabalhar.
As regras de *o que* construir estão em `specs/`. Em caso de conflito, prevalece
[`specs/01-constitution.md`](specs/01-constitution.md). As referências "(Art. N)" apontam para o
artigo da constituição que cada regra aplica.

## Visão rápida do projeto

| Serviço | Papel | Banco |
|---|---|---|
| `api-conhecimento` | Base de conhecimento, chunking, embeddings, pgvector, **MCP Server** com a ferramenta de busca semântica. | PostgreSQL + pgvector |
| `api-atendimentos` | API REST de perguntas, **MCP Client**, Spring AI + OpenAI para gerar respostas. | Nenhum |

## Regras obrigatórias

### Antes de implementar

1. **Leia as specs** (Art. 17). Antes de implementar qualquer funcionalidade, leia:
   - [`specs/01-constitution.md`](specs/01-constitution.md): princípios obrigatórios.
   - [`specs/02-requirements.md`](specs/02-requirements.md): requisitos e critérios de aceite.
   - [`specs/03-architecture.md`](specs/03-architecture.md): arquitetura e versões.
   - [`specs/06-api-contracts.md`](specs/06-api-contracts.md): contratos REST e MCP.

   Consulte também `04-plan.md`, `05-tasks.md`, `07-test-plan.md` e `08-checklist.md` quando a
   tarefa envolver planejamento, testes ou entrega.

2. **Consulte as skills.** Use as skills em `skills/` que se aplicam à tarefa:

   | Skill | Quando usar |
   |---|---|
   | `skills/spring-ai/` | `ChatClient`, prompts, configuração da OpenAI, embeddings. |
   | `skills/mcp-integration/` | MCP Server na `api-conhecimento` e MCP Client na `api-atendimentos`. |
   | `skills/rag-pgvector/` | Chunking, ingestão, `PgVectorStore`, busca semântica, migrações. |
   | `skills/testing/` | Testes unitários, de integração (Testcontainers), de contrato e de aceite. |

   Se a skill estiver vazia ou contradizer as specs, siga as specs e registre isso nas pendências.

3. **Respeite o escopo.** Implemente somente o que a tarefa atual pede. Não adicione
   funcionalidades, endpoints, configurações ou abstrações "para depois". Ideias fora do escopo
   vão para as pendências.

### Stack e arquitetura

4. **Versões** (Art. 1 e 2). Use Java 21, Spring Boot e Spring AI **nas versões definidas em
   `specs/03-architecture.md`** (Spring AI 2.0.1 e o Spring Boot compatível com ela). As versões
   vêm dos BOMs; não declare versões avulsas de artefatos Spring AI. Acesse modelos só pelas
   abstrações do Spring AI, nunca por SDK da OpenAI ou HTTP direto.

5. **`api-atendimentos` sem banco** (Art. 5 e 6). A `api-atendimentos` não pode ter
   dependência, configuração ou conexão com PostgreSQL, pgvector, JDBC/JPA, ferramentas de
   migração ou `VectorStore`, nem gerar embeddings.

6. **Conhecimento só via MCP** (Art. 4). Toda busca de conhecimento feita pela
   `api-atendimentos` passa pela ferramenta do MCP Server da `api-conhecimento` (MCP sobre HTTP,
   Streamable HTTP). Não use acesso direto ao banco, endpoints REST internos nem cópias locais da
   base.

### Qualidade de código

7. **Compilável, organizado e testável** (Art. 3). Todo código entregue compila, segue a
   separação em camadas (controller → serviço de aplicação → domínio → infraestrutura) e pode ser
   testado sem a OpenAI real.

8. **Convenções Java** (Art. 3).
   - Prefira `record` para DTOs de requisição e resposta e para objetos de valor imutáveis.
   - Use injeção por construtor (campos `final`). Não use `@Autowired` em campo.

9. **Segredos e logs seguros** (Art. 7, 8 e 13). Nenhuma API key, token ou senha no código, nos
   testes ou em configuração versionada: use variáveis de ambiente. Por padrão, não registre em
   log:
   - API keys, tokens, senhas ou strings de conexão.
   - Dados pessoais de alunos.
   - O texto completo das perguntas ou das respostas.

   Registre metadados: id de correlação, tamanho da pergunta, ids das fontes, scores, latência,
   tokens e se houve fallback. Registrar o conteúdo completo só pode ser habilitado
   explicitamente por configuração, em ambiente de desenvolvimento.

### Testes, contratos e entrega

10. **Testes para tudo que for implementado** (Art. 14). Crie ou atualize testes para todo
    comportamento novo ou alterado. Os testes automatizados não dependem da OpenAI real (use
    mocks ou stubs de `ChatModel`/`EmbeddingModel`).

11. **Contratos aprovados** (Art. 15 e 16). Não altere endpoints, payloads, códigos de status,
    formato de erro ou o contrato da ferramenta MCP sem:
    1. explicar o motivo da mudança;
    2. atualizar `specs/06-api-contracts.md` (e demais specs afetadas) **antes** do código;
    3. ajustar os testes de contrato.

12. **Verifique antes de concluir** (Art. 14). Antes de dar uma tarefa por concluída, rode a
    compilação e os testes relevantes de cada serviço alterado (por exemplo `./mvnw verify`
    dentro do serviço).

13. **Relatório ao final de cada interação.** Ao terminar cada interação, informe sempre, nesta
    ordem:

    ```markdown
    ### Arquivos criados
    - caminho/do/arquivo: propósito

    ### Arquivos modificados
    - caminho/do/arquivo: o que mudou

    ### Comandos executados
    - comando: resultado (sucesso/falha)

    ### Testes realizados
    - suíte ou classe: resultado real (passou/falhou/não executado e por quê)

    ### Pendências
    - o que ficou de fora, bloqueios, decisões em aberto

    ### Próxima tarefa recomendada
    - próxima tarefa, de preferência referenciando specs/05-tasks.md
    ```

    Se algum item não se aplicar, escreva "Nenhum", sem omitir a seção.

14. **Nada de resultados inventados.** Nunca afirme que a compilação ou os testes passaram sem
    tê-los executado nesta interação. Se não foi possível executar (Docker parado, falta de rede
    etc.), diga isso e mostre o erro.

### Infraestrutura local

15. **Docker Desktop** (Art. 5 e 7). Use Docker (via Docker Desktop) para construir e subir os
    containers e serviços de banco de dados, em especial o PostgreSQL com pgvector (imagem
    `pgvector/pgvector`).
    - Os arquivos de Docker Compose ficam em `infra/`.
    - As credenciais vêm do `.env` (nunca versionado); toda variável nova também entra no
      `.env.example`, sem valor real.
    - Os testes de integração usam Testcontainers, que também depende do Docker Desktop em
      execução.
    - Antes de usar Docker, confirme que ele está ativo (`docker info`).

## Fluxo operacional: Spec → Plan → Implement → Test → Review

Toda tarefa segue estas cinco etapas, nesta ordem. Não pule etapas.

| Etapa | O que fazer | Saída esperada |
|---|---|---|
| **1. Spec** | Ler as specs (regra 1) e as skills relevantes (regra 2). Identificar requisitos, critérios de aceite e contratos envolvidos. Se a spec estiver ausente, ambígua ou conflitante, parar e perguntar. | Lista dos requisitos e critérios de aceite cobertos pela tarefa. |
| **2. Plan** | Planejar a menor mudança que atende a tarefa (regra 3): arquivos a criar ou alterar, testes a escrever, impacto em contratos. Conferir o plano contra a constituição. | Plano curto, alinhado a `specs/04-plan.md` e `specs/05-tasks.md`. |
| **3. Implement** | Implementar só o escopo planejado, seguindo as regras 4 a 9. Mudanças de contrato seguem a regra 11. | Código compilável. |
| **4. Test** | Criar ou atualizar os testes (regra 10). Rodar compilação e testes (regra 12), subindo a infraestrutura com Docker quando necessário (regra 15). Corrigir falhas antes de seguir. | Saída real dos comandos de build e teste (regra 14). |
| **5. Review** | Revisar o diff contra a constituição e `specs/08-checklist.md`: sem segredos, sem acesso a banco na `api-atendimentos`, logs seguros, contratos respeitados, nada fora do escopo. | Relatório final da interação (regra 13). |
