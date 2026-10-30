import test from 'node:test';
import assert from 'node:assert/strict';
import { hasSavedAnalysis, canWriteAnalysis, versionHeader } from '../lib/sample-permissions.ts';
import { snapshotRows, auditDateBounds, formatAuditCount } from '../lib/audit-values.ts';
import type { Sample } from '../lib/soil.ts';
const sample=(value:Partial<Sample> = {}):Sample=>({id:'fixture',recordVersion:'0',code:'AMO-2026-0001',block:'B',plot:'Q',row:'L',crop:'Café',sampledAt:'2026-10-29',temperature:'25',depth:'0-20',createdAt:'2026-10-29T12:00:00Z',analysis:null,analysisUpdatedAt:null,...value});
test('USER só registra primeiro laudo; autoria desconhecida não reabre laudo legado',()=>{
  const pending=sample(); const legacy=sample({analysisUpdatedAt:'2026-10-20T12:00:00Z',analysisCreatedBy:null,analysisCreatedAt:null,analysis:{calcium:'2.5'}});
  assert.equal(canWriteAnalysis('USER',pending),true);assert.equal(canWriteAnalysis('USER',legacy),false);
  assert.equal(canWriteAnalysis('ADMIN',legacy),true);assert.equal(canWriteAnalysis(undefined,pending),false);
  assert.equal(canWriteAnalysis('ROOT',pending),false);assert.equal(canWriteAnalysis('ADMIN',null),false);
  assert.equal(hasSavedAnalysis(legacy),true);assert.equal(hasSavedAnalysis(pending),false);
});
test('versão zero e BIGINT são enviados sem arredondamento',()=>{
  assert.equal(versionHeader('0'),'"0"');assert.equal(versionHeader('9007199254740993'),'"9007199254740993"');
  assert.equal(versionHeader('9223372036854775807'),'"9223372036854775807"');assert.equal(versionHeader('0002'),'"2"');
  for(const value of [undefined,'','-1','1.5','1e2','*','9223372036854775808',Number.MAX_SAFE_INTEGER+1])assert.throws(()=>versionHeader(value));
});
test('comparação de auditoria preserva decimais e mostra unidades alteradas',()=>{
  const before={calcium:'2.5',iron:'12345678901234.123456789',units:{calcium:'cmolc/dm³'},observations:'<script>texto</script>'};
  const after={...before,calcium:'3.5',units:{calcium:'mmolc/dm³'}};
  const changed=snapshotRows(before,after,true);
  assert.deepEqual(changed.map(row=>row.key).sort(),['calcium','units.calcium']);
  assert.equal(changed.find(row=>row.key==='units.calcium')?.label,'Unidade de Cálcio (Ca)');
  assert.equal(snapshotRows(before,after).find(row=>row.key==='iron')?.after,'12345678901234.123456789');
});
test('auditoria distingue valor ausente, nulo e branco e trata criações',()=>{
  const rows=snapshotRows({calcium:null,observations:''},{calcium:'0',organicCarbon:'0.0000000001'});
  assert.equal(rows.find(r=>r.key==='calcium')?.before,'Sem dado');assert.equal(rows.find(r=>r.key==='calcium')?.after,'0');
  assert.equal(rows.find(r=>r.key==='organicCarbon')?.before,'Não registrado');assert.equal(rows.find(r=>r.key==='observations')?.before,'Em branco');
  assert.equal(rows.find(r=>r.key==='observations')?.after,'Não registrado');assert.equal(snapshotRows(null,{calcium:'2.5'})[0].before,'—');
});
test('datas da auditoria incluem o dia inteiro no fuso de Brasília',()=>{
  assert.deepEqual(auditDateBounds('2026-10-29','2026-10-29'),{from:'2026-10-29T00:00:00-03:00',to:'2026-10-29T23:59:59.999999-03:00'});
  assert.deepEqual(auditDateBounds('',''),{from:undefined,to:undefined});
  assert.throws(()=>auditDateBounds('2026-10-30','2026-10-29'));assert.throws(()=>auditDateBounds('2026-02-30',''));
});
test('contagem longa da auditoria permanece exata',()=>{
  assert.equal(formatAuditCount('9007199254740993').replace(/\D/g,''),'9007199254740993');
});
