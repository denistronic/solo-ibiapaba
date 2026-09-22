# Projeto de conclusão

## Configuração

Primeiro, defina uma senha no arquivo `.env`:

```env
POSTGRES_PASSWORD=troque-por-uma-senha-segura
```

## Iniciar o projeto

Depois, execute os seguintes comandos com o Docker Compose:

```bash
docker compose config
docker compose up --build -d
```

## Verificar os containers

Para verificar se os containers estão funcionando corretamente:

```bash
docker compose ps
```

Os três serviços precisam aparecer como `healthy`:

- `database`
- `backend`
- `frontend`

## Verificar as tabelas do PostgreSQL

Execute:

```bash
docker compose exec database sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

Devem aparecer as seguintes tabelas:

```text
flyway_schema_history
sample_sequences
samples
```
