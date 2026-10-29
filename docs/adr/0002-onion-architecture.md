# ADR-0002: Onion architecture com regra de dependência verificada

**Status:** aceita

## Contexto

O projeto precisa de regras de negócio testáveis sem framework e de adaptadores substituíveis (banco, HTTP, broker).

## Decisão

Organizar em quatro camadas: `domain`, `application`, `infrastructure` e `api`. As dependências apontam para o centro. As portas são interfaces da camada `application`; os adaptadores ficam em `infrastructure`. A regra é verificada por testes ArchUnit no build.

## Consequências

- O domínio e a saga são testados com dublês em memória, sem Spring.
- Trocar o HTTP do bureau por outro protocolo afeta apenas um adaptador.
- Há mais classes e mapeamentos (entidade JPA separada do agregado) do que em uma arquitetura em camadas simples.

## Alternativas consideradas

- **Camadas tradicionais (controller, service, repository):** descartada. Misturaria regras de negócio com JPA e Spring.