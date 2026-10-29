# ADR-0005: Resiliência com Resilience4j core e classificação de falhas

**Status:** aceita

## Contexto

O bureau e o serviço de limite podem ficar lentos ou indisponíveis. Tentar sem critério agrava a indisponibilidade; recusas de negócio não devem ser reenviadas.

## Decisão

Usar o Resilience4j **core**, de forma programática. Classificar falhas em transitórias (timeout, conexão recusada, 5xx, 408, 429) e permanentes (demais 4xx e respostas inválidas). Retry com backoff exponencial e jitter, apenas para transitórias. Circuit breaker por serviço, contando apenas falhas transitórias. O retry envolve o circuit breaker, e circuito aberto não é reenviado. Timeouts de conexão e de leitura em todas as chamadas.

## Consequências

- Recusas de negócio falham rápido e não abrem o circuito.
- O comportamento é explícito e testável sem o contexto do Spring.
- As métricas do Resilience4j são publicadas no Micrometer.

## Alternativas consideradas

- **Starter do Resilience4j para Spring Boot:** descartada para evitar acoplamento à versão do Boot.
- **Retry por anotação:** descartada. Esconde a classificação de falhas e dificulta o teste.