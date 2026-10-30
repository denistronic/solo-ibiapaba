# Frontend — permissões e auditoria

Node >=22.13, Next standalone e dependências fixadas pelo package-lock. Inclui criação/consulta/edição conforme perfil, confirmação do primeiro laudo, If-Match, recuperação de conflitos e /admin/auditoria.

Na pasta frontend:

```bash
npm ci
npm test
npm run lint
npm run build
npm run typecheck
npm start
```

Para execução local fora do Docker, npm start usa 127.0.0.1:3000 e a API padrão do build é http://localhost:8080/api. NEXT_PUBLIC_API_URL deve ser definida durante o build para mudar esse endereço. PORT e FRONTEND_HOST controlam a escuta do servidor standalone.

No Compose principal, o frontend é publicado por padrão em localhost:3010 e a API em localhost:8090/api. Siga o README da raiz para gerar .env e iniciar.

npm test executa 16 testes; npm run test:e2e executa 18 cenários Playwright em uma instância completa DESCARTÁVEL, com credenciais explicitamente fornecidas. Para instalar o navegador, use npx playwright install chromium. Informe SOLO_E2E_BASE_URL, SOLO_E2E_API_URL, SOLO_E2E_ADMIN_EMAIL e SOLO_E2E_ADMIN_PASSWORD dessa instância. O teste cria e altera registros fictícios e desativa a conta de teste; não use produção.

O executor Docker completo da raiz instala o navegador na imagem de testes, sem exigir Node/Playwright no host. Veja [o guia de frontend](../docs/FRONTEND-AUDITORIA-ETAPA-4.md) e [o README principal](../README.md).
