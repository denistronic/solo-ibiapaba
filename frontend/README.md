# Solo Ibiapaba — Frontend para o backend Java

Frontend React/Next.js adaptado para consumir a API REST do backend Spring Boot do Solo Ibiapaba.

## Arquitetura

```text
Next.js / React / TypeScript
          |
          | HTTP + JSON
          v
Spring Boot (localhost:8080)
          |
          v
Repositório em memória / PostgreSQL futuramente
```

As regras de interpretação agronômica não são mais calculadas no navegador. O frontend chama:

```text
GET /api/samples/{id}/interpretation
```

O backend Java é a fonte das regras de negócio.

## Requisitos

- Node.js 22.13 ou superior
- npm
- Backend Spring Boot Solo Ibiapaba em execução

## Configuração

Copie a configuração de exemplo:

```bash
cp .env.example .env.local
```

Conteúdo padrão:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## Instalação

```bash
npm install
```

## Desenvolvimento

Primeiro, em um terminal, inicie o backend Java:

```bash
mvn spring-boot:run
```

Depois, dentro deste frontend:

```bash
npm run dev
```

Abra:

```text
http://localhost:3000
```

## Build

```bash
npm run build
```

Para iniciar o build de produção:

```bash
npm start
```

## Endpoints utilizados

```text
GET  /api/health
GET  /api/samples
POST /api/samples
PUT  /api/samples/{id}/analysis
GET  /api/samples/{id}/interpretation
```

## Diferenças em relação ao MVP original

- removido o servidor Node `server/local-api.mjs`;
- removido o armazenamento `data/samples.json`;
- removidos Drizzle/Cloudflare Worker/Vite específicos do MVP;
- chamadas REST centralizadas em `lib/api.ts`;
- `lib/soil.ts` mantém somente tipos e definição dos campos do formulário;
- cálculos agronômicos agora são feitos pelo backend Java;
- URL do backend configurável por `NEXT_PUBLIC_API_URL`;
- interface indica `Backend Java conectado/desconectado`;
- erros de validação retornados pelo Spring são exibidos no frontend.

## Observação sobre persistência

Enquanto o backend Java estiver usando `InMemorySampleRepository`, as amostras desaparecem quando ele é reiniciado. Isso muda quando o PostgreSQL for conectado.
