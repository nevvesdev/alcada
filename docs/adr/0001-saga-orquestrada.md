# ADR-0001: Saga orquestrada para a aprovação de crédito

**Status:** aceita

## Contexto

A aprovação envolve bureau, análise de risco e serviço de limite. Os serviços externos são HTTP e não participam de transações distribuídas, e qualquer etapa pode falhar depois de outras já terem produzido efeito.

## Decisão

Usar uma saga **orquestrada**: o `CreditSagaOrchestrator` conduz as etapas, persiste o estado a cada transição e executa compensações explícitas (liberação da reserva, rejeição com motivo).

## Consequências

- O fluxo inteiro fica legível em um único lugar e pode ser retomado pelo status persistido.
- O orquestrador conhece todos os passos, o que cria um ponto central de acoplamento.
- As portas externas precisam ser idempotentes, porque a retomada repete chamadas.

## Alternativas consideradas

- **Coreografia por eventos:** descartada. Com poucos passos e regras de compensação, o fluxo ficaria difuso e difícil de depurar.
- **Transação distribuída (2PC/XA):** descartada. Os serviços externos são HTTP e não oferecem esse protocolo.