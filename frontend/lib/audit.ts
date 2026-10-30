import { request } from '@/lib/api';
export type Snapshot = Record<string, unknown>;
export type AuditEvent = {
  id: string; sampleId: string; entityType: 'SAMPLE' | 'ANALYSIS'; action: 'CREATE' | 'UPDATE'; recordVersion: string | number;
  actorUserId: string; actorName: string; actorEmail: string; actorRole: 'USER' | 'ADMIN'; occurredAt: string;
  beforeData: Snapshot | null; afterData: Snapshot;
};
export type AuditPage = { items: AuditEvent[]; page: string | number; size: string | number; total: string | number; hasNext: boolean };
export type AuditFilters = { sampleId: string; actorId: string; entityType: string; action: string; fromDate: string; toDate: string };
export function listAuditEvents(filters: AuditFilters, page: number, size: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  for (const field of ['sampleId','actorId','entityType','action'] as const) if (filters[field]) query.set(field, filters[field]);
  const dates = auditDateBounds(filters.fromDate, filters.toDate);
  if (dates.from) query.set('from', dates.from);
  if (dates.to) query.set('to', dates.to);
  return request<AuditPage>(`/audit/events?${query}`, { signal });
}
export { auditDateBounds } from './audit-values.ts';
import { auditDateBounds } from './audit-values.ts';
