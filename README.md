# Alcada

Serviço de aprovação e gestão de linhas de crédito em Java 21 e Spring Boot. Saga orquestrada com compensações, eventos via outbox e requisições idempotentes.

[![CI](https://github.com/nevvesdev/alcada/actions/workflows/ci.yaml/badge.svg)](https://github.com/nevvesdev/alcada/actions/workflows/ci.yaml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.9-6DB33F)
![License](https://img.shields.io/badge/License-MIT-blue)

## Visão geral

O cliente solicita um limite de crédito informando valor, CPF e renda declarada. A solicitação percorre etapas coordenadas por um orquestrador central. Se uma etapa falha, as anteriores são compensadas.

## Fluxo da saga

1. **Solicitação recebida**: status `PENDING`, com chave de idempotência para evitar duplicidade em reenvios.
2. **Consulta ao bureau**: serviço externo simulado de score. Status `BUREAU_CHECKED`.
3. **Análise de risco**: regras sobre score, renda e comprometimento. Pode aprovar, negar ou enviar para revisão. Status `RISK_ANALYZED`.
4. **Reserva de limite**: reserva o valor na conta do cliente. Status `LIMIT_RESERVED`.
5. **Aprovação final**: confirma o limite e emite o evento `CreditLineApproved`. Status `APPROVED`.

### Compensações

- Falha na reserva depois da consulta ao bureau: solicitação `REJECTED` com o motivo registrado.
- Falha na aprovação depois da reserva: a reserva é liberada antes de finalizar.
- Bureau sem resposta: timeout e retry com backoff; esgotadas as tentativas, a solicitação é cancelada.

### Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> BUREAU_CHECKED
    PENDING --> REJECTED
    BUREAU_CHECKED --> RISK_ANALYZED
    BUREAU_CHECKED --> REJECTED
    RISK_ANALYZED --> LIMIT_RESERVED
    RISK_ANALYZED --> UNDER_REVIEW
    RISK_ANALYZED --> REJECTED
    UNDER_REVIEW --> LIMIT_RESERVED
    UNDER_REVIEW --> REJECTED
    LIMIT_RESERVED --> APPROVED
    LIMIT_RESERVED --> REJECTED
    APPROVED --> [*]
    REJECTED --> [*]
```

## Arquitetura

Onion Architecture: as dependências apontam sempre para o centro.

```mermaid
flowchart TB
    api["api (REST)"] --> application
    infrastructure["infrastructure (persistência, bureau, outbox)"] --> application
    application["application (casos de uso, portas, orquestrador)"] --> domain
    domain["domain (entidades, value objects, regras)"]
```

## Stack

Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway, Testcontainers, Docker.

## Como executar

Pré-requisitos: Java 21 e Docker.

```bash
docker-compose up -d
./mvnw spring-boot:run
```

Verificação de saúde:

```bash
curl http://localhost:8080/actuator/health
```

Testes (requerem Docker em execução):

```bash
./mvnw verify
```
## Roadmap

- [x] Fase 0: Scaffold, infraestrutura local e CI
- [x] Fase 1: Domínio: solicitação, máquina de estados e regras
- [ ] Fase 2: Aplicação: casos de uso, portas e orquestrador da saga
- [ ] Fase 3: Persistência: Flyway, JPA e saga state
- [ ] Fase 4: Bureau simulado, timeout e retry
- [ ] Fase 5: Outbox e publicação de eventos
- [ ] Fase 6: API REST e idempotência
- [ ] Fase 7: Hardening: Docker, métricas, cobertura e README final

## 👨‍💻 Desenvolvido por

João Victor · [GitHub](https://github.com/nevvesdev) · [LinkedIn](https://www.linkedin.com/in/nevvesdev/)

## Licença

MIT. Veja o arquivo [LICENSE](LICENSE).