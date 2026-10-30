'use client';
import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { ArrowLeft, ClipboardList } from 'lucide-react';
import AppHeader from '@/components/AppHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { useAuth } from '@/context/AuthContext';
import { listSamples, ApiError } from '@/lib/api';
import { listUsers, type ManagedUser } from '@/lib/users';
import type { Sample } from '@/lib/soil';
import { listAuditEvents, type AuditFilters, type AuditEvent, type AuditPage, auditDateBounds } from '@/lib/audit';
import { snapshotRows, formatAuditCount } from '@/lib/audit-values';
import styles from './auditoria.module.css';

const emptyFilters = (): AuditFilters => ({ sampleId:'', actorId:'', entityType:'', action:'', fromDate:'', toDate:'' });
function when(value: string) { return new Date(value).toLocaleString('pt-BR', { timeZone:'America/Fortaleza', dateStyle:'short', timeStyle:'medium' }); }
export default function AuditPageView() { return <Suspense fallback={<p>Carregando auditoria…</p>}><AuditRoute /></Suspense>; }
function AuditRoute() { const search=useSearchParams(); const sample=search.get('sample') ?? ''; return <AuditView key={sample} initialSample={sample}/>; }
function AuditView({ initialSample }: { initialSample: string }) {
  const { user, loading: authLoading, refreshUser } = useAuth();
  const router = useRouter();
  const [draft,setDraft] = useState<AuditFilters>(()=>({...emptyFilters(),sampleId:initialSample}));
  const [applied,setApplied] = useState<AuditFilters>(()=>({...emptyFilters(),sampleId:initialSample}));
  const [samples,setSamples] = useState<Sample[]>([]), [actors,setActors] = useState<ManagedUser[]>([]);
  const [page,setPage] = useState(0), [size,setSize] = useState(20), [reload,setReload] = useState(0);
  const [result,setResult] = useState<AuditPage | null>(null), [loading,setLoading] = useState(true);
  const [error,setError] = useState(''), [catalogError,setCatalogError] = useState(''), [filterError,setFilterError] = useState('');
  const [selected,setSelected] = useState<AuditEvent | null>(null), [changedOnly,setChangedOnly] = useState(true);
  const admin = user?.role === 'ADMIN';
  useEffect(()=>{ if (!authLoading && !admin) router.replace(user ? '/' : '/login'); },[authLoading,admin,user,router]);
  useEffect(()=>{
    if (!admin) return;
    let cancelled=false;
    void Promise.allSettled([listSamples(),listUsers()]).then(([records,people])=>{
      if(cancelled)return;
      if(records.status==='fulfilled')setSamples(records.value);
      if(people.status==='fulfilled')setActors(people.value);
      setCatalogError(records.status==='rejected'||people.status==='rejected' ? 'Não foi possível carregar todos os filtros. A consulta de eventos continua disponível.' : '');
    });
    return ()=>{cancelled=true;};
  },[admin,reload]);
  useEffect(()=>{
    if(!admin)return;
    const controller=new AbortController();
    void Promise.resolve().then(async()=>{
      if(controller.signal.aborted)return;
      setLoading(true);setError('');setSelected(null);setResult(null);
      try { const data=await listAuditEvents(applied,page,size,controller.signal);if(!controller.signal.aborted)setResult(data); }
      catch(cause) {
        if(controller.signal.aborted)return;
        setError(cause instanceof Error?cause.message:'Não foi possível carregar a auditoria.');
        if(cause instanceof ApiError && cause.status===403) void refreshUser().catch(()=>{});
      } finally { if(!controller.signal.aborted)setLoading(false); }
    });
    return ()=>controller.abort();
  },[admin,applied,page,size,reload,refreshUser]);
  const rows = useMemo(()=> selected ? snapshotRows(selected.beforeData,selected.afterData,selected.action==='UPDATE' && changedOnly) : [],[selected,changedOnly]);
  function apply(event: React.FormEvent) {
    event.preventDefault();
    try { auditDateBounds(draft.fromDate,draft.toDate);setFilterError('');setPage(0);setApplied({...draft}); }
    catch(cause){setFilterError(cause instanceof Error?cause.message:'Período inválido.');}
  }
  if(authLoading || !admin) return <main className="app-shell home-app-shell"><p className={styles.loading}>{authLoading?'Carregando…':'Acesso exclusivo do administrador.'}</p></main>;
  return <main className="app-shell home-app-shell">
    <AppHeader />
    <div className={styles.content}>
      <div className={styles.heading}>
        <div><p>RASTREABILIDADE</p><h1><ClipboardList aria-hidden="true" /> Auditoria de registros</h1><span>Criações e edições de amostras e laudos. Horários de Brasília.</span></div>
        <Button variant="outline" onClick={()=>router.push('/')}><ArrowLeft size={17}/> Voltar ao sistema</Button>
      </div>
      <section className={styles.panel} aria-labelledby="audit-filters">
        <h2 id="audit-filters">Filtrar eventos</h2>
        {catalogError && <p role="status">{catalogError}</p>}
        <form onSubmit={apply} className={styles.filters}>
          <div><Label htmlFor="audit-sample">Amostra</Label><NativeSelect id="audit-sample" value={draft.sampleId} onChange={e=>setDraft({...draft,sampleId:e.target.value})}>
            <option value="">Todas as amostras</option>
            {draft.sampleId && !samples.some(s=>s.id===draft.sampleId) && <option value={draft.sampleId}>{draft.sampleId}</option>}
            {samples.map(s=><option key={s.id} value={s.id}>{s.code}{s.serviceOrderNumber?` · OS-${s.serviceOrderNumber}`:''}</option>)}
          </NativeSelect></div>
          <div><Label htmlFor="audit-actor">Responsável</Label><NativeSelect id="audit-actor" value={draft.actorId} onChange={e=>setDraft({...draft,actorId:e.target.value})}>
            <option value="">Todos os responsáveis</option>{actors.map(actor=><option key={actor.id} value={actor.id}>{actor.name} · {actor.email}{actor.active?'':' (inativo)'}</option>)}
          </NativeSelect></div>
          <div><Label htmlFor="audit-entity">Tipo de registro</Label><NativeSelect id="audit-entity" value={draft.entityType} onChange={e=>setDraft({...draft,entityType:e.target.value})}><option value="">Todos os tipos</option><option value="SAMPLE">Amostra</option><option value="ANALYSIS">Laudo</option></NativeSelect></div>
          <div><Label htmlFor="audit-action">Ação</Label><NativeSelect id="audit-action" value={draft.action} onChange={e=>setDraft({...draft,action:e.target.value})}><option value="">Todas as ações</option><option value="CREATE">Criação</option><option value="UPDATE">Edição</option></NativeSelect></div>
          <div><Label htmlFor="audit-from">Data inicial</Label><Input id="audit-from" type="date" value={draft.fromDate} onChange={e=>setDraft({...draft,fromDate:e.target.value})}/></div>
          <div><Label htmlFor="audit-to">Data final</Label><Input id="audit-to" type="date" value={draft.toDate} onChange={e=>setDraft({...draft,toDate:e.target.value})}/></div>
          <div className={styles.actions}>
            <Button type="submit">Aplicar filtros</Button><Button type="button" variant="outline" onClick={()=>{const clean=emptyFilters();setDraft(clean);setApplied(clean);setPage(0);setFilterError('');}}>Limpar filtros</Button>
            <Button type="button" variant="outline" onClick={()=>setReload(value=>value+1)}>Atualizar eventos</Button>
          </div>
        </form>
        {filterError && <p role="alert" className="form-error">{filterError}</p>}
      </section>
      <section className={styles.panel} aria-labelledby="audit-results" aria-busy={loading}>
        <div className={styles.resultsHeading}><h2 id="audit-results">Eventos registrados{result?` (${formatAuditCount(result.total)})`:''}</h2>
          <div><Label htmlFor="audit-size">Eventos por página</Label><NativeSelect id="audit-size" value={size} onChange={e=>{setSize(Number(e.target.value));setPage(0);}}>{[10,20,50,100].map(n=><option key={n} value={n}>{n}</option>)}</NativeSelect></div>
        </div>
        {loading && <p role="status">Carregando eventos…</p>}
        {error && <p role="alert" className="form-error">{error}</p>}
        {!loading && !error && result?.items.length===0 && <p>Nenhum evento encontrado. Registros anteriores à implantação da auditoria não possuem histórico retroativo.</p>}
        {!loading && !error && result?.items.map(event=>{
          const sample=samples.find(s=>s.id===event.sampleId);
          return <article key={event.id} className={styles.event}>
            <div className={styles.eventTop}><strong>{event.entityType==='SAMPLE'?'Amostra':'Laudo'} · {event.action==='CREATE'?'Criação':'Edição'}</strong><time dateTime={event.occurredAt}>{when(event.occurredAt)}</time></div>
            <p>{sample?.code ?? event.sampleId}{sample?.serviceOrderNumber?` · OS-${sample.serviceOrderNumber}`:''} · Versão {event.recordVersion}</p>
            <p><strong>{event.actorName}</strong> · {event.actorEmail} · {event.actorRole==='ADMIN'?'Administrador':'Usuário'}</p>
            <div className={styles.actions}><Button variant="outline" onClick={()=>{setSelected(event);setChangedOnly(true);}}>Ver alterações</Button><Button variant="outline" onClick={()=>router.push(`/?sample=${encodeURIComponent(event.sampleId)}&view=${event.entityType==='SAMPLE'?'cadastro':'analise'}`)}>Abrir registro</Button></div>
          </article>;
        })}
        {result && !loading && !error && <nav aria-label="Paginação da auditoria" className={styles.pagination}>
          <Button variant="outline" disabled={page===0} onClick={()=>setPage(value=>value-1)}>Página anterior</Button><span>Página {page+1}</span><Button variant="outline" disabled={!result.hasNext || page>=1000000} onClick={()=>setPage(value=>value+1)}>Próxima página</Button>
        </nav>}
      </section>
      {selected && <section className={styles.panel} aria-labelledby="audit-details">
        <div className={styles.resultsHeading}><h2 id="audit-details">Detalhes da alteração</h2><Button variant="outline" onClick={()=>setSelected(null)}>Fechar detalhes</Button></div>
        <p>{selected.entityType==='SAMPLE'?'Amostra':'Laudo'} · {selected.action==='CREATE'?'Criação':'Edição'} · {selected.actorName} · {when(selected.occurredAt)} · Versão {selected.recordVersion}</p>
        {selected.action==='UPDATE' && <label className={styles.check}><input type="checkbox" checked={changedOnly} onChange={e=>setChangedOnly(e.target.checked)}/> Mostrar somente campos alterados</label>}
        {selected.action==='CREATE' && <p>Primeiro registro: não há valor anterior.</p>}
        <div className={styles.tableWrap}><table><thead><tr><th>Campo</th><th>Antes</th><th>Depois</th></tr></thead><tbody>{rows.map(row=><tr key={row.key} className={row.changed?styles.changed:undefined}><th scope="row">{row.label}</th><td>{row.before}</td><td>{row.after}</td></tr>)}</tbody></table></div>
      </section>}
    </div>
  </main>;
}
