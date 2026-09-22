Projeto de conclusão

Tem que definir uma senha em .env

POSTGRES_PASSWORD=troque-por-uma-senha-segura


depois é só dar os comandos no Docker Compose:

``
docker compose config
docker compose up --build -d
``

Depois é verificar se o container:

``
docker compose ps
``
Os três precisam aparecer como healthy:

database
backend
frontend


Verificar as tabelas:
``
docker compose exec database sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
``
Devem aparecer:

flyway_schema_history
sample_sequences
samples

Verificar o backend:
``
curl http://127.0.0.1:8080/api/health
``
Resultado esperado:
``
{"status":"ok"}
``
Verificar logs:

docker compose logs --tail=100 backend
docker compose logs --tail=50 frontend
docker compose logs --tail=50 database

Acessar o sistema:

http://localhost:3000