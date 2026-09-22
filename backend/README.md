# Solo Ibiapaba Backend

Backend Java do MVP **Solo Ibiapaba**, migrado a partir do servidor local Node/JSON e das regras de interpretação que estavam em `lib/soil.ts`.

## Stack

- Java 25
- Spring Boot 4.1.1
- Spring MVC / REST
- Jakarta Bean Validation
- Spring Boot Actuator
- Spring JDBC
- PostgreSQL 17
- Flyway
- Maven
- Persistência PostgreSQL; repositório em memória apenas no perfil de testes

## Requisitos

No Fedora, com o ambiente já instalado:

```bash
java -version
javac -version
mvn -version
```

O projeto requer Java 25.

Também é necessário um PostgreSQL acessível. No pacote Docker completo, banco,
backend e frontend são iniciados juntos por `docker compose up --build -d`.

## Executar

Entre na pasta do projeto:

```bash
cd solo-ibiapaba-backend
```

Rode os testes:

```bash
mvn test
```

Inicie a API:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/solo_ibiapaba
export SPRING_DATASOURCE_USERNAME=solo_app
export SPRING_DATASOURCE_PASSWORD=sua_senha
mvn spring-boot:run
```

A API ficará disponível em:

```text
http://localhost:8080
```

## Endpoints

### Saúde

```http
GET /api/health
```

Resposta:

```json
{
  "status": "ok"
}
```

### Listar amostragens

```http
GET /api/samples
```

### Buscar uma amostragem

```http
GET /api/samples/{id}
```

### Criar amostragem

```http
POST /api/samples
Content-Type: application/json
```

Exemplo:

```json
{
  "block": "C",
  "plot": "Q12",
  "row": "L08",
  "crop": "Acerola",
  "sampledAt": "2026-09-08",
  "temperature": 27,
  "depth": "0–20 cm"
}
```

O código é gerado automaticamente no formato:

```text
AMO-2026-0001
```

A sequência é independente para cada ano.

### Salvar laudo

```http
PUT /api/samples/{id}/analysis
Content-Type: application/json
```

Exemplo:

```json
{
  "phWater": 5.2,
  "phCaCl2": 4.8,
  "organicMatter": 25,
  "phosphorus": 20,
  "potassium": 117.3,
  "sodium": 23,
  "calcium": 3,
  "magnesium": 1,
  "aluminum": 0.2,
  "hAl": 2.6,
  "sulfur": 8,
  "boron": 0.4,
  "copper": 0.8,
  "iron": 25,
  "manganese": 8,
  "zinc": 1,
  "cecReported": null,
  "baseSatReported": null,
  "clay": 35,
  "sand": 50,
  "silt": 15,
  "ec": 0.4
}
```

### Obter interpretação calculada

```http
GET /api/samples/{id}/interpretation
```

Retorna a mesma estrutura lógica que a função `analyzeSoil()` do MVP original:

```json
{
  "summary": [],
  "indicators": [],
  "insights": [],
  "derived": []
}
```

## Cálculos migrados do MVP

O `SoilInterpretationService` preserva os cálculos existentes:

- K em cmolc/dm³ = K em mg/dm³ / 391
- Na em cmolc/dm³ = Na em mg/dm³ / 230
- Soma de bases (SB) = Ca + Mg + K + Na
- CTC efetiva (t) = SB + Al
- CTC pH 7 (T) = SB + H+Al
- V% = 100 × SB / T
- m% = 100 × Al / t
- Ca/Mg
- Ca/K
- Mg/K

Também foram migrados os alertas automáticos do MVP:

- acidez ativa;
- alumínio relevante;
- desequilíbrio Ca/Mg;
- competição K × Mg;
- risco de salinidade;
- ausência de alerta crítico automático.

## Compatibilidade com o frontend atual

O frontend original utilizava:

```text
http://localhost:3001/api
```

Este backend utiliza, por padrão:

```text
http://localhost:8080/api
```

No frontend, basta alterar:

```ts
const API="http://localhost:3001/api";
```

para:

```ts
const API="http://localhost:8080/api";
```

Os endpoints principais foram mantidos compatíveis:

```text
GET  /api/samples
POST /api/samples
PUT  /api/samples/{id}/analysis
```

## Testes rápidos com curl

### 1. Verificar a API

```bash
curl http://localhost:8080/api/health
```

### 2. Criar uma amostragem

```bash
curl -i -X POST http://localhost:8080/api/samples \
  -H 'Content-Type: application/json' \
  -d '{
    "block":"C",
    "plot":"Q12",
    "row":"L08",
    "crop":"Acerola",
    "sampledAt":"2026-09-08",
    "temperature":27,
    "depth":"0–20 cm"
  }'
```

Copie o `id` retornado.

### 3. Enviar o laudo

```bash
curl -i -X PUT http://localhost:8080/api/samples/SEU_UUID/analysis \
  -H 'Content-Type: application/json' \
  -d '{
    "phWater":5.2,
    "phosphorus":20,
    "potassium":117.3,
    "sodium":23,
    "calcium":3,
    "magnesium":1,
    "aluminum":0.2,
    "hAl":2.6,
    "ec":0.4
  }'
```

### 4. Obter a interpretação

```bash
curl http://localhost:8080/api/samples/SEU_UUID/interpretation
```

## Importante sobre a persistência atual

Nesta etapa os registros ficam apenas na memória da JVM. Ao encerrar a aplicação, as amostragens são apagadas. Isso é proposital para permitir desenvolver e testar o backend antes de configurar o banco.

A próxima etapa será substituir apenas:

```text
InMemorySampleRepository
```

por uma implementação com:

```text
Spring Data JPA
PostgreSQL
Flyway
```

mantendo os controllers, DTOs e serviços.

## Estrutura

```text
src/main/java/br/com/soloibiapaba/
├── SoloIbiapabaApplication.java
├── config/
│   └── CorsConfig.java
├── controller/
│   ├── HealthController.java
│   └── SampleController.java
├── domain/
│   ├── Sample.java
│   └── SoilAnalysis.java
├── dto/
│   ├── CreateSampleRequest.java
│   ├── SampleResponse.java
│   ├── SoilAnalysisRequest.java
│   └── SoilReportResponse.java
├── exception/
│   ├── AnalysisNotFoundException.java
│   ├── ApiErrorResponse.java
│   ├── GlobalExceptionHandler.java
│   └── SampleNotFoundException.java
├── repository/
│   ├── InMemorySampleRepository.java
│   └── SampleRepository.java
└── service/
    ├── SampleService.java
    └── SoilInterpretationService.java
```

## Escopo agronômico

As classificações são uma triagem geral herdada do MVP. Para uso operacional, as faixas e recomendações devem ser validadas conforme método laboratorial, textura, cultura, produtividade esperada, histórico da área e responsável agronômico.
