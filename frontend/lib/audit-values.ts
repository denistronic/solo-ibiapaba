import { ANALYSIS_FIELDS } from './soil.ts';
export function auditDateBounds(fromDate: string, toDate: string): { from?: string; to?: string } {
  for (const value of [fromDate, toDate]) {
    if (value && (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(Date.parse(value+'T12:00:00Z')) || new Date(value+'T12:00:00Z').toISOString().slice(0,10)!==value)) throw new Error('Informe uma data válida.');
  }
  if (fromDate && toDate && fromDate > toDate) throw new Error('A data inicial não pode ser posterior à data final.');
  return { from: fromDate ? `${fromDate}T00:00:00-03:00` : undefined, to: toDate ? `${toDate}T23:59:59.999999-03:00` : undefined };
}
const names: Record<string, string> = { id:'Identificador', code:'Código', serviceOrderNumber:'Número da OS', block:'Bloco', plot:'Quadra', row:'Linha', crop:'Cultura', sampledAt:'Data da coleta', temperature:'Temperatura (°C)', depth:'Profundidade', observations:'Observações', phWater:'pH em água', density:'Densidade (kg/dm³)', phosphorusMethod:'Extrator de fósforo', ...Object.fromEntries(ANALYSIS_FIELDS.map(f=>[f.key,f.label])) };
function flatten(value: Record<string, unknown> | null): Map<string, unknown> {
  const out = new Map<string, unknown>();
  function visit(object: Record<string, unknown>, prefix = '') {
    for (const [key, item] of Object.entries(object)) {
      const path = prefix ? `${prefix}.${key}` : key;
      if (item !== null && typeof item === 'object' && !Array.isArray(item)) visit(item as Record<string, unknown>, path);
      else out.set(path, item);
    }
  }
  if (value) visit(value);
  return out;
}
export function displayAuditValue(value: unknown): string {
  if (value === undefined) return 'Não registrado';
  if (value === null) return 'Sem dado';
  if (value === '') return 'Em branco';
  if (typeof value === 'boolean') return value ? 'Sim' : 'Não';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}
export function snapshotRows(before: Record<string, unknown> | null, after: Record<string, unknown>, changedOnly = false) {
  const old = flatten(before), next = flatten(after);
  return [...new Set([...old.keys(), ...next.keys()])].map(key=>({
    key, label: key.startsWith('units.') ? `Unidade de ${names[key.slice(6)] ?? key.slice(6)}` : names[key] ?? key,
    before: before === null ? '—' : displayAuditValue(old.get(key)), after: displayAuditValue(next.get(key)),
    changed: !old.has(key) || !next.has(key) || JSON.stringify(old.get(key)) !== JSON.stringify(next.get(key)),
  })).filter(row=>!changedOnly || row.changed);
}
export function formatAuditCount(value: string | number): string {
  try { return BigInt(value).toLocaleString('pt-BR'); } catch { return String(value); }
}
