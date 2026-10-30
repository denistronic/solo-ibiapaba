import type { Sample } from './soil.ts';
export function hasSavedAnalysis(sample: Pick<Sample, 'analysisUpdatedAt'> | null): boolean { return Boolean(sample?.analysisUpdatedAt); }
export function canWriteAnalysis(role: string | undefined, sample: Sample | null): boolean {
  return Boolean(sample && (role === 'ADMIN' || (role === 'USER' && !hasSavedAnalysis(sample))));
}
export function versionHeader(version: string | number | undefined): string {
  if (typeof version === 'number' && !Number.isSafeInteger(version)) throw new Error('Versão inválida. Reabra a amostra.');
  const value = String(version ?? '');
  if (!/^\d+$/.test(value) || BigInt(value) > BigInt('9223372036854775807')) throw new Error('Versão indisponível. Reabra a amostra antes de salvar.');
  return `"${BigInt(value).toString()}"`;
}
