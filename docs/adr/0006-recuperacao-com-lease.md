# ADR-0006: Recuperação de sagas paradas com lease e SKIP LOCKED

**Status:** aceita

## Contexto

Se o processo cai no meio da saga, a solicitação fica parada. A retomada precisa funcionar com várias instâncias sem processar a mesma solicitação ao mesmo tempo, e sem segurar locks de banco durante chamadas HTTP.

## Decisão

Um job reivindica solicitações não terminais e desatualizadas (exceto `UNDER_REVIEW`) com `SELECT ... FOR UPDATE SKIP LOCKED` e grava `claimed_until` na mesma transação curta. O lock do banco dura milissegundos; a exclusividade vem do lease. O orquestrador retoma pelo status persistido, e as portas externas são idempotentes por identificador da solicitação.

## Consequências

- Várias instâncias podem rodar o job sem coordenação externa.
- Um worker que morre libera a solicitação sozinho quando o lease expira.
- A recuperação depende de as chamadas externas serem idempotentes.

## Alternativas consideradas

- **Manter o lock de linha durante o processamento:** descartada. Prende uma conexão durante chamadas HTTP.
- **Biblioteca de lock de jobs (ShedLock):** resolve apenas a execução única do job, não a distribuição por solicitação.