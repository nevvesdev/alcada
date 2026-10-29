# ADR-0003: Outbox transacional para publicação de eventos

**Status:** aceita

## Contexto

Gravar o estado no banco e publicar no broker não é atômico. Uma falha entre os dois deixa o sistema inconsistente (evento perdido ou evento sem estado).

## Decisão

O agregado registra eventos de domínio. O adaptador de persistência grava o estado e as linhas do outbox na **mesma transação**. Um relay lê em lote com `FOR UPDATE SKIP LOCKED` e publica, com limite de tentativas. Os eventos só são limpos do agregado depois do commit.

## Consequências

- Nenhum evento é perdido nem publicado sem o estado correspondente.
- A entrega é *at-least-once*: os consumidores devem deduplicar pelo `eventId`.
- Eventos que esgotam as tentativas ficam na tabela e aparecem na métrica `alcada.outbox.dead`.
- A ordem global não é garantida quando há falhas.

## Alternativas consideradas

- **Publicar no broker após o commit:** descartada. Reproduz o problema de dual write.
- **CDC (por exemplo Debezium):** válida em escala maior, mas adiciona infraestrutura desproporcional ao escopo atual.