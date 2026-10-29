# ADR-0004: Idempotência por chave no cliente

**Status:** aceita

## Contexto

Clientes reenviam requisições por timeout ou falha de rede. Duplicar uma solicitação de crédito é inaceitável.

## Decisão

Exigir o header `Idempotency-Key`. A chave tem restrição `UNIQUE` no banco. A repetição com o mesmo payload devolve a solicitação existente (`200`, sem reprocessar); a repetição com payload diferente é recusada (`422`). Em uma corrida, o conflito do banco é traduzido em `IdempotencyKeyConflictException` e o caso de uso devolve a solicitação vencedora.

## Consequências

- A garantia vale mesmo com várias instâncias, porque a restrição está no banco.
- O cliente precisa gerar e guardar a chave.
- A comparação de payload exige guardar os dados originais da solicitação.

## Alternativas consideradas

- **Deduplicar por hash do payload:** descartada. Duas solicitações legítimas idênticas seriam confundidas.
- **Cache em memória:** descartada. Não sobrevive a reinícios nem funciona entre instâncias.