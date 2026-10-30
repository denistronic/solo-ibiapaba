import test from "node:test";
import assert from "node:assert/strict";
import { decimal, boundedDecimal, compareDecimal, passwordError, rangeMaximum, businessDate } from "../lib/validation.ts";
import { analysisDraft, buildAnalysisPayload } from "../lib/analysis.ts";
import { parseApiJson } from "../lib/json.ts";

test("preserva decimais além da precisão do Number e normaliza expoentes", () => {
  assert.equal(decimal("12345678901234.1234567890", "Valor"), "12345678901234.123456789");
  assert.equal(decimal("1e-10", "Valor"), "0.0000000001");
  assert.equal(decimal("-.2500", "Valor"), "-0.25");
  assert.equal(compareDecimal("0.3000000001", "0.3"), 1);
});
test("rejeita valores inválidos, precisão excessiva e limites físicos", () => {
  for (const value of ["NaN", "Infinity", "1e999999", "1.00000000001", "123456789012345"]) assert.throws(() => decimal(value,"Valor"));
  assert.throws(() => boundedDecimal("101", "Textura", {max:"100"}));
  assert.throws(() => boundedDecimal("25.123", "Temperatura", {fraction:2}));
  assert.equal(boundedDecimal("-20", "Temperatura", {min:"-20",max:"80",fraction:2}),"-20");
});
test("monta laudo sem converter valores originais e preserva precisão", () => {
  const payload = buildAnalysisPayload({calcium:"2.5", organicCarbon:"0.0000000001", units:{calcium:"cmolc/dm³"}});
  assert.equal(payload.calcium,"2.5");
  assert.equal(payload.organicCarbon,"0.0000000001");
  assert.equal(payload.units?.calcium,"cmolc/dm³");
  assert.equal(payload.potassium,null);
  assert.equal(payload.phosphorusMethod,"Não informado");
});
test("exige densidade para resultados por massa e rejeita densidade zero", () => {
  assert.throws(() => buildAnalysisPayload({sulfur:"8",units:{sulfur:"mg/kg"}}),/densidade/);
  assert.equal(buildAnalysisPayload({sulfur:"8",units:{sulfur:"mg/kg"},density:"1.2"}).density,"1.2");
  assert.throws(() => buildAnalysisPayload({density:"0"}),/maior que zero/);
});
test("valida unidade e textura considerando a unidade escolhida", () => {
  assert.throws(() => buildAnalysisPayload({calcium:"1",units:{calcium:"mg/kg"}}),/Unidade/);
  assert.throws(() => buildAnalysisPayload({clay:"101",units:{clay:"%"}}),/intervalo/);
  assert.equal(buildAnalysisPayload({clay:"350"}).clay,"350");
  assert.throws(() => buildAnalysisPayload({phCaCl2:"14.1"}));
});
test("parser da API mantém precisão decimal e os demais tipos JSON", () => {
  const decoded=parseApiJson<{n:string; active:boolean; absent:null; name:string}>('{"n":12345678901234.1234567890,"active":true,"absent":null,"name":"Café"}');
  assert.equal(decoded.n,"12345678901234.1234567890");assert.equal(decoded.active,true);assert.equal(decoded.absent,null);assert.equal(decoded.name,"Café");
});
test("senha usa limite UTF-8 e não apenas quantidade de caracteres", () => {
  assert.equal(passwordError("é".repeat(36)),null);
  assert.match(passwordError("é".repeat(37))!,/72 bytes/);
  assert.notEqual(passwordError("curta"),null);
  assert.equal(passwordError("curta",false),null);
});
test("limites de parâmetros correspondem aos máximos do backend", () => {
  assert.equal(rangeMaximum("Saturação por bases","%"),"100");
  assert.equal(rangeMaximum("Argila","g/kg"),"1000");
  assert.equal(rangeMaximum("pH em água (calculado)",""),"14");
});
test("data inicial segue Fortaleza, inclusive antes da meia-noite local", () => {
  assert.equal(businessDate(new Date("2026-10-30T01:00:00Z")),"2026-10-29");
});

test("reabre formulário mantendo decimais e unidades sem zeros artificiais", () => {
  const draft=analysisDraft({calcium:"2.5000000000",organicCarbon:"1E-10",iron:"12345678901234.1234567890",units:{calcium:"cmolc/dm³"}});
  assert.equal(draft.calcium,"2.5");assert.equal(draft.organicCarbon,"0.0000000001");
  assert.equal(draft.iron,"12345678901234.123456789");assert.equal(draft.units?.calcium,"cmolc/dm³");
});
