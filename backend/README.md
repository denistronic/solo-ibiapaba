Atualização da etapa 5: 61 testes padrão e seis JDBC/Flyway aprovados; dois JDBC de concorrência aguardam PostgreSQL nativo. A auditoria permite somente GET administrativo e nega gravações diretas. Consulte `../docs/TESTES-PERMISSOES-AUDITORIA-ETAPA-5.md`. As evidências de etapas anteriores abaixo são históricas.

> Etapa 3: USER só cria o primeiro laudo; edições existentes são exclusivas de ADMIN com If-Match. GET /api/audit/events é exclusivo de ADMIN. Consulte ../docs/BACKEND-AUDITORIA-ETAPA-3.md para o contrato atual. A interface correspondente foi entregue na etapa 4; consulte ../docs/FRONTEND-AUDITORIA-ETAPA-4.md.

# Backend Solo Ibiapaba

API Java 25/Spring Boot 4.1.1 com PostgreSQL, JDBC/JPA, Flyway e autenticação JWT. A persistência padrão é PostgreSQL. H2 e repositório em memória são exclusivos dos testes com perfil `test`.

Para executar o conjunto com Docker, siga o [README principal](../README.md). A porta interna do backend é 8080, publicada por padrão em http://localhost:8090. Saúde simples: `/api/health`; saúde incluindo banco: `/actuator/health`.

## Desenvolvimento e testes

Com Java 25 e Maven instalados, na pasta `backend`:

```bash
mvn test
```

São 60 testes padrão. Os testes `BackendPostgresIT` e `PostgresMigrationIT` exigem banco descartável e seleção explícita. O executor [integracao/testar-backend-docker.sh](../integracao/testar-backend-docker.sh) roda 68 testes (60 padrão e oito de integração, incluindo concorrência nativa) usando PostgreSQL 17 e Maven em containers. O executor completo é descrito no README principal. Nunca aponte testes de integração para produção.

Para iniciar a API fora do Docker, forneça `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `ADMIN_NAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` e `CORS_ALLOWED_ORIGIN_PATTERNS` no ambiente e execute `mvn spring-boot:run`. O `.env` da raiz é usado pelo Compose; Spring Boot não o carrega automaticamente fora dele.

## Contratos principais

Login público: `POST /api/auth/login`, com e-mail/senha. Perfil autenticado: `GET /api/auth/me`. Nos demais endpoints, envie `Authorization: Bearer <token>`.

| Recurso | Operações |
|---|---|
| Usuários, ADMIN | GET/POST `/api/users`; GET/PUT `/api/users/{id}`; PUT `/api/users/{id}/status` |
| Culturas e faixas | GET `/api/interpretation-parameters/cultures`; GET `/api/interpretation-parameters?crop=...`; POST/PUT `/api/interpretation-parameters` para ADMIN |
| Amostras | GET/POST `/api/samples`; GET `/api/samples/{id}`; PUT `/api/samples/{id}` para ADMIN com If-Match |
| Laudo | PUT `/api/samples/{id}/analysis` |
| Interpretação | GET `/api/samples/{id}/interpretation` |

A cultura precisa estar cadastrada antes de criar amostra. Exemplo de corpo de criação:

```json
{
  "serviceOrderNumber": "00123456",
  "block": "B1",
  "plot": "Q1",
  "row": "L1",
  "crop": "Café",
  "sampledAt": "2026-10-29",
  "temperature": 25.25,
  "depth": "0-20 cm",
  "observations": "Coleta manual."
}
```

Exemplo de laudo com unidades explícitas:

```json
{
  "phCaCl2": "4.8",
  "organicCarbon": "0.0000000001",
  "calcium": "2.5",
  "phosphorus": "20",
  "clay": "35",
  "density": "1.2",
  "phosphorusMethod": "Mehlich 1",
  "units": {
    "calcium": "cmolc/dm³",
    "phosphorus": "mg/dm³",
    "clay": "%"
  }
}
```

Números JSON e strings decimais são aceitos nos campos numéricos. As strings evitam perda de precisão em clientes JavaScript. Valores originais e unidades são armazenados; a interpretação normaliza uma cópia. Unidades omitidas seguem o contrato integrado: massa em g/dm³, cargas em mmolc/dm³ e textura em g/kg. Não envie valores antigos sem informar a unidade correspondente.

O PUT do laudo substitui o conjunto informado; campos omitidos ficam ausentes, salvo a regra explícita de preservação do pH histórico em água. Não há PATCH de laudo nem redefinição de senha pela API desta entrega. A OS preserva zeros e não é única.

As migrações V1–V7 são versionadas e não devem ser reescritas. Conversão de dados antigos e rollback estão descritos em [ATUALIZACAO-MIGRACAO.md](../docs/ATUALIZACAO-MIGRACAO.md).
