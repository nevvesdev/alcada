# ADR-0007: Criptografia do CPF em repouso

**Status:** aceita

## Contexto

O CPF é dado pessoal. Um vazamento do banco ou de um backup não pode expor os documentos em texto puro.

## Decisão

Criptografar o CPF com AES-256-GCM por meio de um `AttributeConverter` do JPA. A chave é derivada de um segredo fornecido por variável de ambiente (`ALCADA_CPF_SECRET`), obrigatório no perfil `container`. O valor guardado leva o prefixo versionado `enc:v1:` e um IV aleatório por gravação. A leitura aceita valores legados em texto puro. As respostas da API e os logs mostram apenas o CPF mascarado, e os eventos não carregam CPF.

## Consequências

- Não é possível consultar nem indexar por CPF no banco.
- A rotação do segredo exige migração de re-cifragem (próxima versão do prefixo).
- Valores adulterados são detectados pelo GCM e geram erro em vez de dado corrompido.

## Alternativas consideradas

- **Criptografia no banco (pgcrypto):** descartada. A chave passaria pelo SQL e pelos logs do banco.
- **Tokenização ou KMS gerenciado:** é a evolução natural para produção, mas exige infraestrutura externa.