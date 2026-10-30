import { chromium } from "playwright";
import assert from "node:assert/strict";
import { decimal, businessDate } from "../lib/validation.ts";

const base = process.env.SOLO_E2E_BASE_URL ?? "http://127.0.0.1:3000";
const api = process.env.SOLO_E2E_API_URL ?? "http://127.0.0.1:8080/api";
const email = process.env.SOLO_E2E_ADMIN_EMAIL;
const password = process.env.SOLO_E2E_ADMIN_PASSWORD;
if (!email || !password) throw new Error("Defina SOLO_E2E_ADMIN_EMAIL/PASSWORD para uma instância DESCARTÁVEL de teste.");
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
  args: process.env.PLAYWRIGHT_CHROMIUM_ARGS ? JSON.parse(process.env.PLAYWRIGHT_CHROMIUM_ARGS) : [],
});
const context = await browser.newContext({viewport:{width:1280,height:900}});
const page = await context.newPage();
page.setDefaultTimeout(20000);
const pageErrors=[];
page.on("pageerror",error=>pageErrors.push(error.message));
const suffix=Date.now();
const crop=`Café E2E ${suffix}`;
const readerEmail=`leitor-${suffix}@example.test`;
const readerPassword="SenhaTeste123";
let adminToken, readerId, sampleId, userSampleId;
let steps=0;
async function step(name,run) { await run();steps++;console.log(`PASS ${name}`); }
async function login(loginEmail,loginPassword) {
  await page.goto(`${base}/login`);
  await page.getByLabel("E-mail",{exact:true}).fill(loginEmail);
  await page.getByLabel("Senha",{exact:true}).fill(loginPassword);
  await page.getByRole("button",{name:"Entrar",exact:true}).click();
  await page.getByRole("heading",{name:"Nova Amostragem",exact:true}).waitFor();
}
async function apiRequest(path,method="GET",body) {
  const headers={Authorization:`Bearer ${adminToken}`};
  if(method==="PUT" && path.startsWith("/samples/")) {
    const current=await apiRequest(path.replace(/\/analysis$/,""));
    headers["If-Match"]=String(current.recordVersion);
  }
  const response=await context.request.fetch(api+path,{method,headers,data:body});
  assert.ok(response.ok(),`${method} ${path}: HTTP ${response.status()} ${await response.text()}`);
  return response.json();
}
try {
  await step("login e sessão restaurada",async()=>{
    await login(email,password);
    adminToken=await page.evaluate(()=>localStorage.getItem("solo-ibiapaba:token"));
    assert.ok(adminToken);
    await page.reload();
    await page.getByRole("button",{name:"Gerenciar usuários",exact:true}).waitFor();
  });
  await step("senha UTF-8, criação de usuário e e-mail duplicado",async()=>{
    await page.getByRole("button",{name:"Gerenciar usuários",exact:true}).click();
    await page.getByLabel("Nome",{exact:true}).fill("Leitor E2E");
    await page.getByLabel("E-mail",{exact:true}).fill(readerEmail);
    await page.getByLabel("Senha inicial",{exact:true}).fill("é".repeat(37));
    await page.getByRole("button",{name:"Criar usuário",exact:true}).click();
    await page.getByRole("alert").filter({hasText:"72 bytes"}).waitFor();
    await page.getByLabel("Senha inicial",{exact:true}).fill(readerPassword);
    const response=page.waitForResponse(r=>r.url().endsWith("/api/users")&&r.request().method()==="POST");
    await page.getByRole("button",{name:"Criar usuário",exact:true}).click();
    readerId=(await (await response).json()).id;
    await page.getByRole("cell",{name:readerEmail,exact:true}).waitFor();
    await page.getByLabel("Nome",{exact:true}).fill("Duplicado");
    await page.getByLabel("E-mail",{exact:true}).fill(readerEmail);
    await page.getByLabel("Senha inicial",{exact:true}).fill(readerPassword);
    await page.getByRole("button",{name:"Criar usuário",exact:true}).click();
    await page.getByRole("alert").filter({hasText:"Já existe um usuário"}).waitFor();
  });
  await step("cadastro de cultura e parâmetros",async()=>{
    await page.getByRole("button",{name:"Parâmetros de interpretação",exact:true}).click();
    await page.getByRole("button",{name:"Adicionar",exact:true}).click();
    await page.getByLabel("Nome da Cultura",{exact:true}).fill(crop);
    await page.getByLabel("Mínimo de Cálcio (Ca)",{exact:true}).fill("20");
    await page.getByLabel("Máximo de Cálcio (Ca)",{exact:true}).fill("30");
    await page.getByRole("button",{name:"Salvar",exact:true}).click();
    await page.getByText(`Parâmetros de ${crop} salvos com sucesso.`,{exact:true}).waitFor();
  });
  await step("amostra com OS e observações",async()=>{
    await page.getByRole("button",{name:"Voltar",exact:true}).click();
    await page.getByLabel("Número da OS",{exact:true}).fill("00123456");
    await page.getByLabel("Bloco",{exact:true}).fill("B1");
    await page.getByLabel("Quadra",{exact:true}).fill("Q1");
    await page.getByLabel("Linha",{exact:true}).fill("L1");
    await page.getByLabel("Cultura",{exact:true}).selectOption(crop);
    await page.getByLabel("Data da coleta",{exact:true}).fill("2026-10-29");
    await page.getByLabel("Temperatura (°C)",{exact:true}).fill("25.25");
    await page.getByLabel("Observações",{exact:true}).fill("Solo úmido.\nColeta manual.");
    const response=page.waitForResponse(r=>r.url().endsWith("/api/samples")&&r.request().method()==="POST");
    await page.getByRole("button",{name:"Gerar amostra e salvar",exact:true}).click();
    sampleId=(await (await response).json()).id;
    await page.waitForURL(url=>url.searchParams.get("sample")===sampleId);
    await page.getByRole("heading",{name:"Resultados laboratoriais",exact:true}).waitFor();
    assert.equal(await page.getByLabel("Extrator de fósforo",{exact:true}).inputValue(),"Não informado");
  });
  await step("densidade obrigatória e laudo nas unidades originais",async()=>{
    await page.getByLabel("Unidade de Enxofre (S)",{exact:true}).selectOption("mg/kg");
    await page.getByLabel("Enxofre (S)",{exact:true}).fill("8");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
    await page.getByRole("alert").filter({hasText:"densidade"}).waitFor();
    await page.getByLabel("Densidade de Conversão (kg/dm³)",{exact:true}).fill("1.2");
    for(const [label,value] of [["pH em CaCl₂","4.8"],["Matéria orgânica","25"],["Carbono orgânico total","0.0000000001"],["Fósforo (P)","20"],["Cálcio (Ca)","2.5"],["Magnésio (Mg)","10"],["Potássio (K)","2"],["Sódio (Na)","1"],["Alumínio total (Al)","2"],["Acidez total (H+Al)","30"],["Argila","35"],["Silte","15"],["Areia total","50"],["Ferro (Fe)","12345678901234.1234567890"]])
      await page.getByLabel(label,{exact:true}).fill(value);
    for(const [label,unit] of [["Cálcio (Ca)","cmolc/dm³"],["Fósforo (P)","mg/dm³"],["Argila","%"],["Silte","%"],["Areia total","%"]])
      await page.getByLabel(`Unidade de ${label}`,{exact:true}).selectOption(unit);
    await page.getByLabel("Extrator de fósforo",{exact:true}).selectOption("Mehlich 1");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    const calcium=page.getByRole("row").filter({has:page.getByRole("cell",{name:"Cálcio (Ca)",exact:true})});
    assert.match(await calcium.textContent(),/25.00 mmolc\/dm³/);
    assert.match(await calcium.textContent(),/Médio/);
    await page.getByRole("cell",{name:"Fósforo (P) — Mehlich 1",exact:true}).waitFor();
    await page.getByText("Solo úmido.\nColeta manual.",{exact:true}).waitFor();
  });
  await step("reabrir e editar sem perder valores ou unidades",async()=>{
    await page.reload();
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    await page.getByRole("tab",{name:/Laudo/}).click();
    assert.equal(decimal(await page.getByLabel("Cálcio (Ca)",{exact:true}).inputValue(),"Cálcio"),"2.5");
    assert.equal(await page.getByLabel("Unidade de Cálcio (Ca)",{exact:true}).inputValue(),"cmolc/dm³");
    assert.equal(decimal(await page.getByLabel("Ferro (Fe)",{exact:true}).inputValue(),"Ferro"),"12345678901234.123456789");
    assert.equal(decimal(await page.getByLabel("Carbono orgânico total",{exact:true}).inputValue(),"Carbono"),"0.0000000001");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
  });
  await step("histórico, paginação, filtro por OS e abertura da amostra",async()=>{
    for(let index=0;index<10;index++) await apiRequest("/samples","POST",{serviceOrderNumber:String(90000000+index),block:"B",plot:"Q",row:"L",crop,sampledAt:"2026-10-29",temperature:"25",depth:"0-20 cm"});
    await page.getByRole("button",{name:"Histórico de amostras",exact:true}).click();
    await page.getByRole("button",{name:"Próxima página",exact:true}).click();
    await page.getByLabel("Pesquisar",{exact:true}).fill("00123456");
    await page.getByRole("row").filter({hasText:"OS-00123456"}).waitFor();
    assert.equal(await page.getByRole("button",{name:"Próxima página",exact:true}).isDisabled(),true);
    await page.getByRole("row").filter({hasText:"OS-00123456"}).getByRole("button",{name:"Abrir",exact:true}).click();
    await page.waitForURL(url=>url.searchParams.get("sample")===sampleId);
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
  });
  await step("layout móvel e impressão",async()=>{
    await page.setViewportSize({width:390,height:844});
    if(process.env.SOLO_E2E_SCREENSHOT) await page.screenshot({path:process.env.SOLO_E2E_SCREENSHOT,fullPage:true});
    const overflow=await page.evaluate(()=>({width:window.innerWidth,scroll:document.documentElement.scrollWidth,elements:[...document.querySelectorAll("main *")].filter(el=>el.getBoundingClientRect().right>window.innerWidth+1).slice(0,12).map(el=>({tag:el.tagName,cls:el.className,right:el.getBoundingClientRect().right}))}));
    assert.ok(overflow.scroll<=overflow.width+1,`Página sem transbordamento horizontal: ${JSON.stringify(overflow)}`);
    await page.emulateMedia({media:"print"});
    assert.equal(await page.getByRole("button",{name:"Sair da conta",exact:true}).isVisible(),false);
    await page.emulateMedia({media:"screen"});
    if(process.env.SOLO_E2E_SCREENSHOT) await page.screenshot({path:process.env.SOLO_E2E_SCREENSHOT,fullPage:true});
    await page.setViewportSize({width:1280,height:900});
  });
  await step("usuário comum consulta parâmetros sem ações administrativas",async()=>{
    await page.getByRole("button",{name:"Sair da conta",exact:true}).click();
    await login(readerEmail,readerPassword);
    assert.equal(await page.getByRole("button",{name:"Gerenciar usuários",exact:true}).count(),0);
    await page.getByRole("button",{name:"Parâmetros de interpretação",exact:true}).click();
    await page.getByText("Modo de consulta.",{exact:false}).waitFor();
    await page.getByLabel("Selecione uma cultura",{exact:true}).selectOption(crop);
    await page.getByRole("button",{name:"Carregar",exact:true}).click();
    await page.getByRole("heading",{name:`Indicadores — ${crop}`,exact:true}).waitFor();
    assert.equal(await page.getByRole("button",{name:"Adicionar",exact:true}).count(),0);
    assert.equal(await page.getByRole("button",{name:"Editar",exact:true}).count(),0);
  });
  await step("USER consulta registros existentes sem edição nem acesso à auditoria",async()=>{
    await page.goto(`${base}/?sample=${sampleId}`);
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    await page.getByRole("tab",{name:/Laudo/}).click();
    await page.getByText("Laudo registrado — modo de consulta.",{exact:false}).waitFor();
    assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).isDisabled(),true);
    assert.equal(await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).count(),0);
    await page.getByRole("tab",{name:/Amostragem/}).click();
    assert.equal(await page.getByLabel("Bloco",{exact:true}).isDisabled(),true);
    assert.equal(await page.getByRole("button",{name:"Editar dados da amostra",exact:true}).count(),0);
    assert.equal(await page.getByRole("button",{name:"Auditoria de registros",exact:true}).count(),0);
    let auditRequests=0;const observe=request=>{if(request.url().includes("/audit/events"))auditRequests++;};
    page.on("request",observe);
    await page.goto(`${base}/admin/auditoria`);
    await page.waitForURL(`${base}/`);await page.getByRole("heading",{name:"Nova Amostragem",exact:true}).waitFor();
    page.off("request",observe);assert.equal(auditRequests,0);
  });
  await step("USER cria amostra e confirma o primeiro laudo sem poder editá-lo depois",async()=>{
    for(const [label,value] of [["Número da OS","00770011"],["Bloco","BU"],["Quadra","QU"],["Linha","LU"],["Temperatura (°C)","24.25"]])await page.getByLabel(label,{exact:true}).fill(value);
    await page.getByLabel("Cultura",{exact:true}).selectOption(crop);
    const created=page.waitForResponse(r=>r.url().endsWith("/api/samples")&&r.request().method()==="POST");
    await page.getByRole("button",{name:"Gerar amostra e salvar",exact:true}).click();
    userSampleId=(await (await created).json()).id;
    await page.getByRole("heading",{name:"Resultados laboratoriais",exact:true}).waitFor();
    await page.getByLabel("Cálcio (Ca)",{exact:true}).fill("2.5");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
    await page.getByRole("dialog",{name:"Confirmar primeiro laudo",exact:true}).waitFor();
    await page.getByRole("button",{name:"Cancelar",exact:true}).click();
    assert.equal((await apiRequest(`/samples/${userSampleId}`)).analysisUpdatedAt,null);
    assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).inputValue(),"2.5");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
    const saved=page.waitForResponse(r=>r.url().endsWith(`/samples/${userSampleId}/analysis`)&&r.request().method()==="PUT");
    await page.getByRole("button",{name:"Confirmar registro do laudo",exact:true}).click();
    const response=await saved;assert.equal(response.status(),200);assert.equal(response.request().headers()["if-match"],'"1"');
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    await page.getByRole("tab",{name:/Laudo/}).click();
    assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).isDisabled(),true);
    assert.equal(await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).count(),0);
  });
  await step("ADMIN filtra auditoria e consulta autoria do primeiro laudo USER",async()=>{
    await page.getByRole("button",{name:"Sair da conta",exact:true}).click();await login(email,password);
    await page.getByRole("button",{name:"Auditoria de registros",exact:true}).click();
    await page.getByRole("heading",{name:"Auditoria de registros",exact:true}).waitFor();
    await page.getByLabel("Amostra",{exact:true}).selectOption(userSampleId);
    await page.getByLabel("Responsável",{exact:true}).selectOption(readerId);
    await page.getByLabel("Tipo de registro",{exact:true}).selectOption("ANALYSIS");
    await page.getByLabel("Ação",{exact:true}).selectOption("CREATE");
    await page.getByLabel("Data inicial",{exact:true}).fill(businessDate());await page.getByLabel("Data final",{exact:true}).fill(businessDate());
    await page.getByRole("button",{name:"Aplicar filtros",exact:true}).click();
    await page.getByRole("heading",{name:"Eventos registrados (1)",exact:true}).waitFor();
    await page.getByText("Leitor E2E",{exact:true}).waitFor();
    await page.getByRole("button",{name:"Ver alterações",exact:true}).click();
    await page.getByRole("heading",{name:"Detalhes da alteração",exact:true}).waitFor();
    await page.getByRole("cell",{name:"2.5",exact:true}).waitFor();
    const query=await apiRequest(`/audit/events?sampleId=${userSampleId}`);
    assert.equal(query.items.find(event=>event.entityType==="ANALYSIS").actorUserId,readerId);
  });
  await step("ADMIN edita cadastro com versão e reenvio igual não cria evento",async()=>{
    await page.goto(`${base}/?sample=${userSampleId}&view=cadastro`);
    await page.getByRole("button",{name:"Editar dados da amostra",exact:true}).click();
    await page.getByLabel("Bloco",{exact:true}).fill("B2");
    await page.getByLabel("Observações",{exact:true}).fill("Correção administrativa.");
    let pending=page.waitForResponse(r=>r.url().endsWith(`/samples/${userSampleId}`)&&r.request().method()==="PUT");
    await page.getByRole("button",{name:"Salvar dados da amostra",exact:true}).click();
    const response=await pending;assert.equal(response.status(),200);assert.equal(response.request().headers()["if-match"],'"2"');
    await page.getByRole("button",{name:"Editar dados da amostra",exact:true}).waitFor();
    let stored=await apiRequest(`/samples/${userSampleId}`);assert.equal(stored.block,"B2");assert.equal(stored.recordVersion,3);
    const count=(await apiRequest(`/audit/events?sampleId=${userSampleId}`)).total;
    await page.getByRole("button",{name:"Editar dados da amostra",exact:true}).click();
    pending=page.waitForResponse(r=>r.url().endsWith(`/samples/${userSampleId}`)&&r.request().method()==="PUT");
    await page.getByRole("button",{name:"Salvar dados da amostra",exact:true}).click();assert.equal((await pending).status(),200);
    stored=await apiRequest(`/samples/${userSampleId}`);assert.equal(stored.recordVersion,3);
    assert.equal((await apiRequest(`/audit/events?sampleId=${userSampleId}`)).total,count);
  });
  await step("ADMIN corrige laudo e compara valores anteriores e novos",async()=>{
    await page.getByRole("tab",{name:/Laudo/}).click();await page.getByLabel("Cálcio (Ca)",{exact:true}).fill("3.5");
    const pending=page.waitForResponse(r=>r.url().endsWith(`/samples/${userSampleId}/analysis`)&&r.request().method()==="PUT");
    await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();const response=await pending;assert.equal(response.status(),200);assert.equal(response.request().headers()["if-match"],'"3"');
    await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    await page.getByRole("button",{name:"Auditoria desta amostra",exact:true}).click();
    await page.getByRole("heading",{name:"Eventos registrados (4)",exact:true}).waitFor();
    await page.getByLabel("Tipo de registro",{exact:true}).selectOption("ANALYSIS");await page.getByLabel("Ação",{exact:true}).selectOption("UPDATE");
    await page.getByRole("button",{name:"Aplicar filtros",exact:true}).click();await page.getByRole("heading",{name:"Eventos registrados (1)",exact:true}).waitFor();
    await page.getByRole("button",{name:"Ver alterações",exact:true}).click();
    const row=page.getByRole("row").filter({has:page.getByRole("rowheader",{name:"Cálcio (Ca)",exact:true})});
    assert.match(await row.textContent(),/2.5.*3.5/);
    assert.equal(await page.getByRole("rowheader",{name:"Unidade de Cálcio (Ca)",exact:true}).count(),0);
    await page.getByLabel("Mostrar somente campos alterados",{exact:true}).uncheck();
    await page.getByRole("rowheader",{name:"Unidade de Cálcio (Ca)",exact:true}).waitFor();
    await page.getByRole("button",{name:"Abrir registro",exact:true}).click();
    await page.getByRole("heading",{name:"Resultados laboratoriais",exact:true}).waitFor();
    assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).isDisabled(),false);
  });
  await step("duas abas detectam versão antiga sem sobrescrever nem perder rascunho",async()=>{
    await page.goto(`${base}/?sample=${userSampleId}`);await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
    await page.getByRole("tab",{name:/Laudo/}).click();await page.getByLabel("Cálcio (Ca)",{exact:true}).fill("4.5");
    const second=await context.newPage();second.on("pageerror",error=>pageErrors.push(error.message));
    try {
      await second.goto(`${base}/?sample=${userSampleId}`);await second.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
      await second.getByRole("tab",{name:/Laudo/}).click();await second.getByLabel("Cálcio (Ca)",{exact:true}).fill("5.5");
      await second.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();await second.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
      const conflict=page.waitForResponse(r=>r.url().endsWith(`/samples/${userSampleId}/analysis`)&&r.request().method()==="PUT");
      await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();assert.equal((await conflict).status(),409);
      await page.getByRole("alert").filter({hasText:"rascunho continua"}).waitFor();
      assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).inputValue(),"4.5");
      assert.equal(await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).isDisabled(),true);
      assert.equal((await apiRequest(`/samples/${userSampleId}`)).analysis.calcium,5.5);
      await page.getByRole("button",{name:"Reabrir registro atualizado",exact:true}).click();await page.getByRole("button",{name:"Cancelar",exact:true}).click();
      assert.equal(await page.getByLabel("Cálcio (Ca)",{exact:true}).inputValue(),"4.5");
      await page.getByRole("button",{name:"Reabrir registro atualizado",exact:true}).click();await page.getByRole("button",{name:"Descartar rascunho e reabrir",exact:true}).click();
      await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();await page.getByRole("tab",{name:/Laudo/}).click();
      assert.equal(decimal(await page.getByLabel("Cálcio (Ca)",{exact:true}).inputValue(),"Cálcio"),"5.5");
      await page.getByLabel("Cálcio (Ca)",{exact:true}).fill("6.5");await page.getByRole("button",{name:"Salvar e interpretar",exact:true}).click();
      await page.getByRole("heading",{name:"Interpretação dos indicadores",exact:true}).waitFor();
      assert.equal((await apiRequest(`/samples/${userSampleId}`)).analysis.calcium,6.5);
    } finally {await second.close();}
  });
  await step("auditoria preserva precisão e paginação, valida período e funciona no celular",async()=>{
    await page.getByRole("button",{name:"Auditoria de registros",exact:true}).click();await page.getByRole("heading",{name:"Filtrar eventos",exact:true}).waitFor();
    await page.getByLabel("Eventos por página",{exact:true}).selectOption("10");await page.getByRole("button",{name:"Próxima página",exact:true}).click();
    await page.getByText("Página 2",{exact:true}).waitFor();
    await page.getByLabel("Amostra",{exact:true}).selectOption(sampleId);await page.getByLabel("Tipo de registro",{exact:true}).selectOption("ANALYSIS");await page.getByLabel("Ação",{exact:true}).selectOption("CREATE");
    await page.getByRole("button",{name:"Aplicar filtros",exact:true}).click();await page.getByRole("heading",{name:"Eventos registrados (1)",exact:true}).waitFor();
    await page.getByText("Página 1",{exact:true}).waitFor();assert.equal(await page.getByRole("button",{name:"Página anterior",exact:true}).isDisabled(),true);
    await page.getByRole("button",{name:"Ver alterações",exact:true}).click();await page.getByRole("cell",{name:"12345678901234.123456789",exact:true}).waitFor();
    await page.getByRole("cell",{name:"0.0000000001",exact:true}).waitFor();
    await page.setViewportSize({width:390,height:844});
    const sizes=await page.evaluate(()=>({width:window.innerWidth,scroll:document.documentElement.scrollWidth}));assert.ok(sizes.scroll<=sizes.width+1,JSON.stringify(sizes));
    await page.setViewportSize({width:1280,height:900});
    await page.getByLabel("Data inicial",{exact:true}).fill("2026-10-30");await page.getByLabel("Data final",{exact:true}).fill("2026-10-29");
    await page.getByRole("button",{name:"Aplicar filtros",exact:true}).click();await page.getByRole("alert").filter({hasText:"posterior"}).waitFor();
    await page.getByRole("button",{name:"Limpar filtros",exact:true}).click();
  });
  await step("seleção relê o registro e mantém unidade e profundidade históricas",async()=>{
    let s=await apiRequest(`/samples/${userSampleId}`);
    await apiRequest(`/samples/${userSampleId}`,"PUT",{serviceOrderNumber:s.serviceOrderNumber,block:s.block,plot:"Q atualizado",row:s.row,crop:s.crop,sampledAt:s.sampledAt,temperature:s.temperature,depth:"0-20 cm histórico",observations:s.observations});
    await page.goto(`${base}/?sample=${userSampleId}&view=cadastro`);await page.getByRole("button",{name:"Editar dados da amostra",exact:true}).waitFor();
    assert.equal(await page.getByLabel("Quadra",{exact:true}).inputValue(),"Q atualizado");assert.equal(await page.getByLabel("Profundidade",{exact:true}).inputValue(),"0-20 cm histórico");
    await page.getByRole("button",{name:"Sair da conta",exact:true}).click();await login(readerEmail,readerPassword);
  });
  await step("conta desativada encerra sessão ao receber 401",async()=>{
    await apiRequest(`/users/${readerId}/status`,"PUT",{active:false});
    await page.reload();
    await page.waitForURL(`${base}/login`);
    await page.getByRole("button",{name:"Entrar",exact:true}).waitFor();
    assert.equal(await page.evaluate(()=>localStorage.getItem("solo-ibiapaba:token")),null);
  });
  assert.deepEqual(pageErrors,[]);
  console.log(JSON.stringify({status:"aprovado",cenarios:steps,errosJavaScript:pageErrors.length}));
} catch(error) {
  console.error(`FALHA após ${steps} cenários: ${error.message}`);
  console.error(`URL: ${page.url()}`);
  throw error;
} finally { await browser.close(); }
