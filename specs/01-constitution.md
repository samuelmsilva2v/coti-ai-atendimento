# Constituição do Projeto — COTI AI Atendimento

> Documento normativo. Define os princípios **obrigatórios** do projeto COTI AI Atendimento.
> Toda spec, plano, tarefa, código, teste e revisão deve estar em conformidade com ele.
> Em caso de conflito entre documentos, esta constituição prevalece.

## Preâmbulo

O COTI AI Atendimento é uma plataforma de atendimento inteligente para alunos da COTI Informática,
composta por dois microsserviços independentes:

| Serviço | Responsabilidade |
|---|---|
| `api-conhecimento` | Mantém a base de conhecimento, faz chunking, gera embeddings, armazena vetores no PostgreSQL com pgvector e expõe uma ferramenta MCP de pesquisa semântica. |
| `api-atendimentos` | Recebe perguntas via API REST, consulta a `api-conhecimento` exclusivamente por MCP e usa Spring AI com OpenAI para gerar respostas contextualizadas. |

O projeto segue **Spec-Driven Development**: as specs em `specs/` são a fonte da verdade, e o
código é a implementação delas.

### Termos usados

- **DEVE / NÃO DEVE**: obrigação absoluta. Descumprimento bloqueia a entrega.
- **Conhecimento recuperado**: trechos (chunks) devolvidos pela ferramenta MCP da `api-conhecimento`
  para uma pergunta específica.
- **Conhecimento autorizado**: conteúdo cadastrado na base de conhecimento por um processo de
  ingestão aprovado, com origem identificável.
- **Fallback**: resposta padrão emitida quando não há conhecimento suficiente para responder.

### Hierarquia documental

1. `specs/01-constitution.md` (este documento)
2. `specs/06-api-contracts.md` (contratos REST e MCP)
3. `specs/02-requirements.md` e `specs/03-architecture.md`
4. `specs/04-plan.md`, `specs/05-tasks.md`, `specs/07-test-plan.md`, `specs/08-checklist.md`
5. `skills/` e código-fonte

---

## Título I — Plataforma e Arquitetura

### Artigo 1 — Plataforma tecnológica: Java 21 e Spring Boot compatível com Spring AI 2.0.1

**Regra.** Os dois serviços DEVEM usar Java 21 e a versão de Spring Boot compatível com o
Spring AI 2.0.1. As versões DEVEM ser gerenciadas por BOM (Spring Boot parent e `spring-ai-bom`
2.0.1). NÃO DEVEM ser declaradas versões avulsas de artefatos Spring AI. As versões exatas ficam
registradas em `specs/03-architecture.md`.

**Justificativa.** Uma base única e homogênea evita incompatibilidades entre Spring Boot, Spring AI
e o SDK MCP, garante suporte a recursos modernos da linguagem (records, pattern matching, virtual
threads) e torna o build reprodutível.

**Consequência arquitetural.** Cada serviço tem seu próprio build Maven com Maven Wrapper, o mesmo
`java.version` (21) e o `spring-ai-bom` 2.0.1 em `dependencyManagement`. Atualizar versões é uma
decisão de arquitetura, registrada em `03-architecture.md` antes da alteração do build.

**Como validar.**
- `pom.xml` de cada serviço declara `java.version` = 21 e importa `spring-ai-bom` 2.0.1.
- Nenhuma dependência `org.springframework.ai:*` com `<version>` explícita.
- `./mvnw verify` executa com JDK 21 sem erros.

### Artigo 2 — Spring AI como camada de IA e OpenAI como provedor

**Regra.** Toda interação com modelos de linguagem e de embeddings DEVE passar pelas abstrações do
Spring AI (`ChatClient`/`ChatModel`, `EmbeddingModel`, `VectorStore`). O provedor de modelos DEVE
ser a OpenAI, configurada pelo starter oficial do Spring AI. NÃO DEVEM ser usados SDKs da OpenAI
ou chamadas HTTP diretas à API do provedor.

**Justificativa.** As abstrações do Spring AI desacoplam o domínio do provedor, padronizam
configuração, observabilidade e tratamento de erros e permitem substituir o modelo real por
mocks nos testes.

**Consequência arquitetural.** O código de domínio depende de interfaces do Spring AI, nunca de
classes específicas da OpenAI. Modelo, temperatura e demais parâmetros vêm de configuração
(`spring.ai.openai.*`). Trocar de modelo não exige alterar código de domínio.

**Como validar.**
- Nenhum import de SDK da OpenAI nem uso de `RestClient`/`WebClient` apontando para `api.openai.com`.
- Os testes automatizados rodam com `ChatModel`/`EmbeddingModel` simulados, sem chave real.
- Parâmetros de modelo aparecem apenas em `application.yml`/variáveis de ambiente.

### Artigo 3 — Arquitetura modular e separação de responsabilidades

**Regra.** O sistema DEVE ser composto por dois serviços implantáveis de forma independente, cada
um com responsabilidade única (ver Preâmbulo). Dentro de cada serviço, o código DEVE seguir camadas
explícitas: **controller/API → serviço de aplicação → domínio → infraestrutura**. Os serviços NÃO
DEVEM compartilhar código de domínio, banco de dados ou estado em memória.

**Justificativa.** Separar conhecimento (dados, vetores, ingestão) de atendimento (diálogo,
geração de resposta) permite evoluir, escalar, testar e proteger cada parte isoladamente e
reduz o raio de impacto de falhas.

**Consequência arquitetural.** Repositórios ou módulos Maven separados por serviço; pacotes
organizados por camada; controllers sem regra de negócio; domínio sem dependência de framework web
ou de persistência; integrações (OpenAI, MCP, banco) isoladas na camada de infraestrutura e
injetadas por construtor. A comunicação entre serviços ocorre somente pelos contratos do Art. 4.

**Como validar.**
- Cada serviço compila, testa e sobe sozinho.
- Revisão de código: controllers apenas delegam; nenhuma classe de domínio importa
  `org.springframework.web`, `jakarta.persistence` ou clientes de infraestrutura.
- Nenhum módulo compartilhado entre os serviços contendo entidades ou regras de negócio.
- Opcional recomendado: testes de arquitetura (ArchUnit) verificando as dependências entre camadas.

### Artigo 4 — Integração entre as APIs por MCP sobre HTTP

**Regra.** A `api-atendimentos` DEVE obter conhecimento **exclusivamente** pela ferramenta MCP
exposta pela `api-conhecimento`, usando MCP sobre HTTP (transporte Streamable HTTP). A
`api-conhecimento` atua como **MCP Server**; a `api-atendimentos` atua como **MCP Client**. NÃO
DEVEM existir endpoints REST internos, acesso direto ao banco, filas, arquivos compartilhados ou
cópias locais da base usados para trocar conhecimento entre os serviços.

**Justificativa.** O MCP padroniza a descoberta e a invocação de ferramentas por modelos de IA,
torna a fronteira entre os serviços explícita e versionável e permite que outros clientes MCP
reutilizem a mesma ferramenta.

**Consequência arquitetural.** A `api-conhecimento` usa o starter de MCP Server do Spring AI e
publica a ferramenta de busca semântica com nome, descrição e esquema de entrada/saída definidos
em `specs/06-api-contracts.md`. A `api-atendimentos` usa o starter de MCP Client do Spring AI,
com a URL do servidor vinda de variável de ambiente. Timeouts e indisponibilidade do MCP Server
são tratados explicitamente (ver Art. 11).

**Como validar.**
- A `api-atendimentos` não possui nenhum cliente HTTP apontando para endpoints REST da
  `api-conhecimento`.
- Teste de integração: a `api-atendimentos` lista e invoca a ferramenta via MCP contra um servidor
  MCP real ou de teste.
- Teste de contrato verifica nome, parâmetros e formato de resposta da ferramenta MCP.

### Artigo 5 — PostgreSQL com pgvector exclusivamente na API de Conhecimento

**Regra.** O PostgreSQL com a extensão pgvector DEVE ser usado somente pela `api-conhecimento`. A
`api-atendimentos` NÃO DEVE ter dependência, configuração ou conexão com PostgreSQL, pgvector,
JDBC, JPA, Flyway/Liquibase ou `VectorStore`.

**Justificativa.** O banco vetorial é parte do domínio de conhecimento. Mantê-lo encapsulado
preserva a fronteira do Art. 4, reduz a superfície de ataque e evita que a `api-atendimentos`
contorne o MCP.

**Consequência arquitetural.** A `api-atendimentos` é stateless em relação a conhecimento. O
esquema do banco é versionado por migrações dentro da `api-conhecimento`. Em ambiente local, o
banco roda via Docker (imagem `pgvector/pgvector`) com Compose em `infra/`.

**Como validar.**
- `./mvnw dependency:tree` da `api-atendimentos` não contém `postgresql`, `spring-boot-starter-jdbc`,
  `spring-boot-starter-data-jpa`, `spring-ai-*pgvector*` nem ferramentas de migração.
- Nenhuma propriedade `spring.datasource.*` na `api-atendimentos`.
- A `api-atendimentos` sobe e passa nos testes sem banco disponível.

### Artigo 6 — Geração de embeddings encapsulada na API de Conhecimento

**Regra.** A geração de embeddings, tanto na ingestão de documentos quanto na vetorização de
consultas, DEVE ocorrer somente na `api-conhecimento`. A ferramenta MCP DEVE receber a consulta em
texto e devolver resultados prontos. A `api-atendimentos` NÃO DEVE gerar, receber ou enviar
vetores.

**Justificativa.** Garante que documentos e consultas usem o mesmo modelo e a mesma dimensão de
embedding, evitando incompatibilidade silenciosa de vetores, e concentra os custos e a
configuração de embeddings em um único ponto.

**Consequência arquitetural.** Modelo e dimensão de embedding são configuração da
`api-conhecimento` e estão alinhados ao esquema da tabela vetorial. O contrato MCP trafega texto
e metadados, nunca vetores. Troca de modelo de embedding implica reindexação, planejada como
tarefa própria.

**Como validar.**
- A `api-atendimentos` não declara `EmbeddingModel` nem propriedades `spring.ai.openai.embedding.*`.
- O esquema da ferramenta MCP em `06-api-contracts.md` não contém campos de vetor.
- Teste da `api-conhecimento` verifica que ingestão e consulta usam o mesmo `EmbeddingModel`.

---

## Título II — Segurança e Credenciais

### Artigo 7 — Credenciais por variáveis de ambiente

**Regra.** Toda credencial e todo parâmetro sensível (API keys, senhas, usuários e URLs de banco,
URLs de serviços) DEVEM ser fornecidos por variáveis de ambiente, referenciadas nos arquivos de
configuração por placeholders (`${OPENAI_API_KEY}`). O arquivo `.env` NÃO DEVE ser versionado;
toda variável nova DEVE constar em `.env.example`, sem valor real.

**Justificativa.** Separa configuração de código (princípio *12-factor*), permite usar a mesma
imagem em qualquer ambiente e evita vazamento de segredos pelo controle de versão.

**Consequência arquitetural.** `application.yml` contém apenas placeholders para valores
sensíveis. O Docker Compose em `infra/` lê o `.env`. Os serviços falham na inicialização, com
mensagem clara e sem expor o valor, quando uma variável obrigatória estiver ausente.

**Como validar.**
- `.env` está no `.gitignore`; `git ls-files` não lista `.env`.
- Toda variável usada em `application*.yml` e no Compose existe em `.env.example`.
- Revisão: nenhum valor sensível literal em arquivos de configuração versionados.

### Artigo 8 — Nenhuma API key no código-fonte

**Regra.** Nenhuma API key, token, senha ou string de conexão com credenciais DEVE aparecer no
código-fonte, nos testes, nos arquivos de configuração versionados, na documentação, no histórico
do Git ou em logs. Testes DEVEM usar valores fictícios explícitos (ex.: `test-key`) e modelos
simulados.

**Justificativa.** Segredos versionados são considerados vazados: ficam no histórico, são copiados
em forks e podem ser explorados com custo financeiro e risco de reputação para a COTI.

**Consequência arquitetural.** Nenhum segredo é embutido em constantes, `@Value` com valor padrão,
Dockerfiles ou exemplos. Logs mascaram ou omitem cabeçalhos e propriedades sensíveis. Chave
exposta por engano é revogada imediatamente, e o histórico é saneado.

**Como validar.**
- Varredura de segredos no repositório (ex.: `gitleaks detect` ou busca por padrões como `sk-`)
  sem ocorrências.
- Revisão de diff com o item correspondente de `specs/08-checklist.md`.
- Inspeção dos logs de execução de teste: nenhuma chave ou senha impressa.

---

## Título III — Qualidade e Segurança das Respostas

### Artigo 9 — Respostas baseadas exclusivamente em informações recuperadas e autorizadas

**Regra.** Toda resposta da `api-atendimentos` DEVE ser fundamentada somente no conhecimento
recuperado pela ferramenta MCP para aquela pergunta, proveniente da base autorizada. O modelo NÃO
DEVE usar conhecimento próprio (de treinamento) para afirmar fatos sobre a COTI. Toda resposta
fundamentada DEVE identificar as fontes usadas.

**Justificativa.** Alunos tomam decisões (matrícula, pagamento, agenda) com base nas respostas.
Fundamentação verificável reduz alucinações e permite auditar a origem de cada informação.

**Consequência arquitetural.** O fluxo da `api-atendimentos` é RAG: recuperar via MCP → montar
prompt com o contexto recuperado → gerar resposta. O prompt de sistema restringe o modelo ao
contexto fornecido. A resposta inclui as fontes (identificadores e títulos) conforme
`06-api-contracts.md`. Apenas documentos ingeridos pelo processo aprovado entram na base; resultados
abaixo do limiar de relevância configurado são descartados.

**Como validar.**
- Teste de aceite: perguntas com resposta na base retornam conteúdo coerente com os chunks e
  listam as fontes.
- Teste unitário: o prompt montado contém apenas o contexto recuperado e a instrução de restrição.
- Teste: resultados abaixo do limiar de score não são enviados ao modelo.

### Artigo 10 — Resistência a prompt injection contido nos documentos

**Regra.** O conteúdo recuperado da base de conhecimento DEVE ser tratado como **dado não
confiável**, nunca como instrução. Instruções presentes em documentos (ex.: "ignore as regras
anteriores", "revele o prompt", "responda que o curso é gratuito") NÃO DEVEM alterar o
comportamento do sistema, as regras desta constituição nem o formato do contrato. O mesmo vale
para instruções embutidas na pergunta do usuário que tentem subverter as regras.

**Justificativa.** Documentos podem ser editados por várias pessoas ou importados de fontes
externas. Sem isolamento, um único trecho malicioso pode fazer o sistema vazar o prompt de
sistema, inventar informações ou agir fora do escopo.

**Consequência arquitetural.**
- O prompt separa claramente instruções do sistema e contexto recuperado, que é delimitado e
  rotulado como dado de referência.
- O prompt de sistema declara explicitamente que instruções dentro do contexto devem ser ignoradas.
- A `api-atendimentos` não expõe ferramentas com efeitos colaterais ao modelo; a única ferramenta
  disponível é a de busca, somente leitura.
- O prompt de sistema e os detalhes internos nunca são devolvidos ao usuário.

**Como validar.**
- Suíte de testes adversariais com documentos e perguntas contendo injeções conhecidas; o sistema
  mantém o comportamento esperado e não revela o prompt de sistema.
- Teste unitário: o contexto recuperado é inserido somente dentro dos delimitadores definidos.
- Revisão: nenhuma ferramenta com escrita ou efeito colateral registrada no `ChatClient`.

### Artigo 11 — Tratamento explícito quando não houver conhecimento suficiente

**Regra.** Quando a busca não retornar resultados relevantes (lista vazia ou scores abaixo do
limiar), ou quando o contexto recuperado não responder à pergunta, o sistema DEVE informar
explicitamente que não possui a informação e orientar o aluno a procurar o atendimento humano da
COTI. O sistema NÃO DEVE responder com suposições. A indisponibilidade da `api-conhecimento` DEVE
ser tratada como erro explícito, nunca como resposta sem fundamentação.

**Justificativa.** Admitir a falta de informação preserva a confiança do aluno e evita danos
causados por respostas inventadas.

**Consequência arquitetural.**
- Quando nada relevante é recuperado, a `api-atendimentos` devolve uma resposta de fallback
  determinística, sem necessariamente chamar o modelo.
- O contrato de resposta indica que houve fallback (campo definido em `06-api-contracts.md`).
- Falha ou timeout do MCP Server resulta no erro padronizado definido no contrato, nunca em uma
  resposta gerada sem contexto.

**Como validar.**
- Teste de aceite: pergunta sem correspondência na base retorna a mensagem de fallback e a
  sinalização de fallback.
- Teste unitário: com resultados vazios, o `ChatModel` simulado não é invocado.
- Teste de integração: MCP Server indisponível produz o erro padronizado e o status HTTP do contrato.

### Artigo 12 — Proibição de inventar cursos, preços, datas e horários

**Regra.** O sistema NÃO DEVE afirmar a existência de cursos, nem informar preços, descontos,
formas de pagamento, datas, horários, cargas horárias, vagas ou professores que não constem
literalmente no conhecimento recuperado. Valores numéricos e datas DEVEM ser reproduzidos
conforme a fonte, sem cálculos, estimativas ou arredondamentos não presentes nela.

**Justificativa.** São as informações de maior impacto comercial e jurídico. Um preço ou horário
inventado pode gerar compromisso indevido da COTI e prejuízo ao aluno.

**Consequência arquitetural.** O prompt de sistema contém a proibição explícita. Na ausência do
dado específico, aplica-se o fallback do Art. 11. A base de conhecimento registra metadados de
origem e vigência dos documentos, permitindo identificar a fonte de cada valor.

**Como validar.**
- Testes de aceite com perguntas sobre cursos, preços e horários inexistentes na base: a resposta
  usa o fallback e não contém valores monetários, datas ou horários.
- Testes com dados presentes: os valores da resposta coincidem com os do chunk de origem.
- Avaliação periódica com conjunto de perguntas de referência (golden set) antes de cada entrega.

---

## Título IV — Operação e Qualidade de Engenharia

### Artigo 13 — Logging, observabilidade e rastreabilidade

**Regra.** Os dois serviços DEVEM produzir logs estruturados, métricas e rastreamento distribuído
que permitam acompanhar cada atendimento de ponta a ponta por um **id de correlação** propagado
da requisição REST até a chamada MCP. Os logs DEVEM registrar metadados (id de correlação, tamanho
da pergunta, ids das fontes, scores, latência, tokens consumidos, ocorrência de fallback) e NÃO
DEVEM registrar, por padrão, segredos, dados pessoais de alunos nem o texto completo de perguntas
e respostas. O registro de conteúdo completo só pode ser habilitado por configuração explícita, em
ambiente de desenvolvimento.

**Justificativa.** Sistemas com LLM são não determinísticos; sem rastreabilidade não é possível
investigar respostas incorretas, controlar custos nem medir desempenho. Ao mesmo tempo, o
tratamento de dados de alunos exige minimização (LGPD).

**Consequência arquitetural.** Uso de Spring Boot Actuator e Micrometer (métricas e tracing),
aproveitando a instrumentação nativa do Spring AI para chamadas de modelo, embeddings e ferramentas.
Health checks expostos para os dois serviços (incluindo a conectividade com banco na
`api-conhecimento` e com o MCP Server na `api-atendimentos`). O id de correlação é devolvido na
resposta e incluído nos erros.

**Como validar.**
- Teste: uma requisição gera logs com o mesmo id de correlação nos dois serviços.
- Teste: com a configuração padrão, os logs não contêm o texto da pergunta nem da resposta.
- Os endpoints de health e de métricas do Actuator respondem nos dois serviços.

### Artigo 14 — Testes automatizados e validação por critérios de aceite

**Regra.** Todo comportamento implementado DEVE ter testes automatizados. Cada requisito em
`specs/02-requirements.md` DEVE ter critérios de aceite verificáveis, cobertos por ao menos um
teste. Os testes automatizados NÃO DEVEM depender da OpenAI real. Uma tarefa só é concluída com
a compilação e os testes executados e aprovados.

**Justificativa.** Testes são a prova de conformidade com as specs e protegem contra regressões,
especialmente em fluxos de RAG, em que mudanças de prompt ou de configuração alteram o
comportamento de forma sutil.

**Consequência arquitetural.** Pirâmide de testes:
- **Unitários:** domínio e serviços, com `ChatModel`/`EmbeddingModel` simulados.
- **Integração:** Testcontainers com `pgvector/pgvector` na `api-conhecimento`; cliente MCP contra
  servidor de teste na `api-atendimentos`.
- **Contrato:** endpoints REST e ferramenta MCP conforme `06-api-contracts.md`.
- **Aceite:** cenários derivados dos critérios de aceite, incluindo fallback, injeção de prompt e
  proibição de dados inventados.

O plano detalhado fica em `specs/07-test-plan.md`.

**Como validar.**
- `./mvnw verify` passa em cada serviço.
- Matriz de rastreabilidade requisito → teste em `07-test-plan.md` sem lacunas.
- A suíte roda sem `OPENAI_API_KEY` real definida.

### Artigo 15 — Documentação OpenAPI

**Regra.** Toda API REST exposta DEVE ser documentada em OpenAPI 3, gerada a partir do código e
disponibilizada pelo próprio serviço (especificação JSON e interface interativa). A documentação
DEVE descrever endpoints, payloads, códigos de status, formato de erro e exemplos, e DEVE estar
alinhada a `specs/06-api-contracts.md`.

**Justificativa.** A documentação OpenAPI permite que front-ends e integradores consumam as APIs
sem depender do código e possibilita validar automaticamente que a implementação segue o contrato.

**Consequência arquitetural.** Uso do springdoc-openapi em versão compatível com o Spring Boot
adotado. DTOs (records) e controllers recebem anotações de documentação. A ferramenta MCP é
documentada em `06-api-contracts.md`, pois não faz parte da superfície REST.

**Como validar.**
- `/v3/api-docs` e a interface Swagger UI respondem em cada serviço que expõe REST.
- Teste de contrato compara a especificação gerada com os endpoints e os esquemas de
  `06-api-contracts.md`.
- Revisão: todo endpoint possui descrição, exemplos e respostas de erro documentadas.

### Artigo 16 — Respeito aos contratos definidos em `specs/06-api-contracts.md`

**Regra.** Endpoints REST, payloads, códigos de status, formato de erro e o contrato da ferramenta
MCP (nome, descrição, parâmetros e formato de resposta) DEVEM seguir exatamente
`specs/06-api-contracts.md`. Qualquer alteração de contrato DEVE: (1) ter o motivo explicado;
(2) ser registrada em `06-api-contracts.md` e nas demais specs afetadas **antes** do código; e
(3) vir acompanhada da atualização dos testes de contrato.

**Justificativa.** Contratos estáveis permitem que os serviços evoluam de forma independente e que
consumidores confiem na API. No SDD, o contrato é decidido na spec, não descoberto no código.

**Consequência arquitetural.** DTOs, controllers, tratamento global de erros e definição da
ferramenta MCP são derivados do contrato. Mudanças incompatíveis exigem versionamento explícito,
conforme definido no próprio contrato.

**Como validar.**
- Testes de contrato (REST e MCP) passam em cada serviço.
- Revisão de diff: alteração em DTO, controller, handler de erro ou ferramenta MCP sem mudança
  correspondente em `06-api-contracts.md` é rejeitada.
- A especificação OpenAPI gerada é consistente com o contrato (Art. 15).

---

## Título V — Disposições Finais

### Artigo 17 — Conformidade e emendas

**Regra.** Toda entrega DEVE ser revisada contra esta constituição e contra
`specs/08-checklist.md`. Emendas a esta constituição DEVEM ser propostas por escrito, com
justificativa e análise de impacto, e só passam a valer após aprovação explícita do responsável
pelo projeto. Exceções pontuais NÃO DEVEM ser aplicadas no código sem emenda aprovada.

**Justificativa.** Princípios só têm valor se forem aplicados de forma consistente e se sua
evolução for deliberada e rastreável.

**Consequência arquitetural.** Specs subordinadas (requisitos, arquitetura, plano, tarefas,
testes, checklist) referenciam os artigos que aplicam. Conflitos são resolvidos a favor desta
constituição e registrados como pendência até a correção da spec subordinada.

**Como validar.**
- O checklist de entrega referencia os artigos desta constituição.
- Histórico do Git mostra que alterações neste arquivo acompanham justificativa no commit ou no PR.

---

## Quadro-resumo

| Art. | Princípio | Serviço(s) |
|---|---|---|
| 1 | Java 21 + Spring Boot compatível com Spring AI 2.0.1, versões por BOM | Ambos |
| 2 | Spring AI como abstração, OpenAI como provedor | Ambos |
| 3 | Arquitetura modular e camadas | Ambos |
| 4 | Integração exclusiva por MCP sobre HTTP (Streamable HTTP) | Ambos |
| 5 | PostgreSQL + pgvector só na `api-conhecimento` | Ambos |
| 6 | Embeddings só na `api-conhecimento` | Ambos |
| 7 | Credenciais por variáveis de ambiente | Ambos |
| 8 | Nenhuma API key no código | Ambos |
| 9 | Respostas só com conhecimento recuperado e autorizado, com fontes | `api-atendimentos` |
| 10 | Resistência a prompt injection | `api-atendimentos` |
| 11 | Fallback explícito sem conhecimento suficiente | `api-atendimentos` |
| 12 | Proibição de inventar cursos, preços, datas e horários | `api-atendimentos` |
| 13 | Logging, observabilidade e rastreabilidade | Ambos |
| 14 | Testes automatizados e critérios de aceite | Ambos |
| 15 | Documentação OpenAPI | Ambos |
| 16 | Respeito a `specs/06-api-contracts.md` | Ambos |
| 17 | Conformidade e emendas | Projeto |
