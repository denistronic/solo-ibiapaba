"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Eye,
  Filter,
  Leaf,
  RotateCcw,
  Search,
  Sprout,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { toast, Toaster } from "sonner";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { ApiError, listSamples } from "@/lib/api";
import type { Sample } from "@/lib/soil";

function formatDate(value: string) {
  return new Date(`${value}T12:00:00`).toLocaleDateString("pt-BR");
}

function messageFromError(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

type SortKey = "sampledAt" | "code" | "crop" | "block";
type SortDirection = "asc" | "desc";

const PAGE_SIZE = 10;

export default function HistoricoPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [samples, setSamples] = useState<Sample[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("todos");
  const [crop, setCrop] = useState("todas");
  const [block, setBlock] = useState("todos");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("sampledAt");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [page, setPage] = useState(1);
  const [preview, setPreview] = useState<Sample | null>(null);

  useEffect(() => {
    if (!authLoading && !user) router.push("/login");
  }, [authLoading, user, router]);

  useEffect(() => {
    if (!user) return;
    async function load() {
      try {
        setSamples(await listSamples());
      } catch (error) {
        toast.error(messageFromError(error, "Não foi possível carregar o histórico."));
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [user]);

  useEffect(() => setPage(1), [query, status, crop, block, fromDate, toDate, sortKey, sortDirection]);

  const crops = useMemo(
    () => [...new Set(samples.map((sample) => sample.crop))].sort((a, b) => a.localeCompare(b)),
    [samples],
  );
  const blocks = useMemo(
    () => [...new Set(samples.map((sample) => sample.block))].sort((a, b) => a.localeCompare(b)),
    [samples],
  );

  const filtered = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const result = samples.filter((sample) => {
      const searchable = `${sample.code} ${sample.block} ${sample.plot} ${sample.row} ${sample.crop}`.toLowerCase();
      return (
        (!normalizedQuery || searchable.includes(normalizedQuery)) &&
        (status === "todos" || (status === "analisadas" ? Boolean(sample.analysis) : !sample.analysis)) &&
        (crop === "todas" || sample.crop === crop) &&
        (block === "todos" || sample.block === block) &&
        (!fromDate || sample.sampledAt >= fromDate) &&
        (!toDate || sample.sampledAt <= toDate)
      );
    });

    return [...result].sort((a, b) => {
      const first = sortKey === "sampledAt" ? a.sampledAt : String(a[sortKey]);
      const second = sortKey === "sampledAt" ? b.sampledAt : String(b[sortKey]);
      const comparison = first.localeCompare(second, "pt-BR", { numeric: true });
      return sortDirection === "asc" ? comparison : -comparison;
    });
  }, [samples, query, status, crop, block, fromDate, toDate, sortKey, sortDirection]);

  const analyzedCount = samples.filter((sample) => sample.analysis).length;
  const pendingCount = samples.length - analyzedCount;
  const cultureCount = new Set(samples.map((sample) => sample.crop)).size;
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const startItem = filtered.length ? (currentPage - 1) * PAGE_SIZE + 1 : 0;
  const endItem = Math.min(currentPage * PAGE_SIZE, filtered.length);
  const hasFilters = Boolean(query || status !== "todos" || crop !== "todas" || block !== "todos" || fromDate || toDate);

  function clearFilters() {
    setQuery("");
    setStatus("todos");
    setCrop("todas");
    setBlock("todos");
    setFromDate("");
    setToDate("");
  }

  function changeSort(nextKey: SortKey) {
    if (sortKey === nextKey) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(nextKey);
    setSortDirection(nextKey === "sampledAt" ? "desc" : "asc");
  }

  function SortButton({ label, column }: { label: string; column: SortKey }) {
    const active = sortKey === column;
    return (
      <button type="button" className={`history-sort ${active ? "active" : ""}`} onClick={() => changeSort(column)}>
        {label}
        {active && (sortDirection === "asc" ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
      </button>
    );
  }

  if (authLoading || !user) {
    return <main className="flex min-h-screen items-center justify-center"><p className="text-sm text-neutral-500">Carregando...</p></main>;
  }

  return (
    <main className="app-shell">
      <Toaster position="top-right" richColors />
      <header className="topbar no-print">
        <div className="brand-mark"><Sprout size={22} /></div>
        <div><strong>Solo Ibiapaba</strong><span>Inteligência para análise de solo</span></div>
        <div className="region-tag"><Leaf size={15} /> Serra da Ibiapaba · CE</div>
        <div className="history-user">
          <span>{user.name} · <strong>{user.role === "ADMIN" ? "Administrador" : "Usuário"}</strong></span>
          <Button variant="outline" size="sm" onClick={() => router.push("/")}>Nova amostragem</Button>
        </div>
      </header>

      <section className="history-page">
        <div className="history-header">
          <div>
            <Button variant="ghost" size="sm" className="back-button" onClick={() => router.push("/")}><ArrowLeft /> Voltar</Button>
            <p>GESTÃO DE FERTILIDADE</p>
            <h1>Histórico de amostras</h1>
            <span>Consulte, filtre e encontre rapidamente as amostragens registradas no sistema.</span>
          </div>
          <div className="history-header-icon"><ClipboardList size={28} /></div>
        </div>

        <div className="history-summary">
          <div><span>Total de amostras</span><strong>{samples.length}</strong><small>registros cadastrados</small></div>
          <div><span>Laudos analisados</span><strong>{analyzedCount}</strong><small>com resultados laboratoriais</small></div>
          <div><span>Aguardando laudo</span><strong>{pendingCount}</strong><small>sem análise registrada</small></div>
          <div><span>Culturas registradas</span><strong>{cultureCount}</strong><small>tipos encontrados</small></div>
        </div>

        <section className="history-filter-card">
          <div className="history-filter-title">
            <div className="history-filter-heading"><Filter size={18} /><div><h2>Filtrar amostras</h2><p>Combine os campos abaixo para localizar um conjunto específico de registros.</p></div></div>
            {hasFilters && <Button variant="ghost" size="sm" onClick={clearFilters}><RotateCcw /> Limpar filtros</Button>}
          </div>
          <div className="history-filters">
            <div className="history-search"><Search size={17} /><Input aria-label="Buscar amostra" placeholder="Código, bloco, quadra, linha ou cultura" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
            <div><label htmlFor="status-filter">Status</label><NativeSelect id="status-filter" value={status} onChange={(event) => setStatus(event.target.value)}><NativeSelectOption value="todos">Todos</NativeSelectOption><NativeSelectOption value="analisadas">Laudo analisado</NativeSelectOption><NativeSelectOption value="pendentes">Aguardando laudo</NativeSelectOption></NativeSelect></div>
            <div><label htmlFor="crop-filter">Cultura</label><NativeSelect id="crop-filter" value={crop} onChange={(event) => setCrop(event.target.value)}><NativeSelectOption value="todas">Todas</NativeSelectOption>{crops.map((item) => <NativeSelectOption key={item} value={item}>{item}</NativeSelectOption>)}</NativeSelect></div>
            <div><label htmlFor="block-filter">Bloco</label><NativeSelect id="block-filter" value={block} onChange={(event) => setBlock(event.target.value)}><NativeSelectOption value="todos">Todos</NativeSelectOption>{blocks.map((item) => <NativeSelectOption key={item} value={item}>Bloco {item}</NativeSelectOption>)}</NativeSelect></div>
            <div><label htmlFor="from-date">Coleta a partir de</label><div className="history-date"><CalendarDays size={15} /><Input id="from-date" type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} /></div></div>
            <div><label htmlFor="to-date">Coleta até</label><div className="history-date"><CalendarDays size={15} /><Input id="to-date" type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} /></div></div>
          </div>
        </section>

        <section className="history-results">
          <div className="history-results-heading">
            <div><h2>Amostras encontradas</h2><span>{startItem}–{endItem} de {filtered.length} registros</span></div>
            <div className="history-result-tools">{hasFilters && <span className="filter-active">Filtro ativo</span>}<span className="history-sort-hint">Clique no cabeçalho para ordenar</span></div>
          </div>
          <div className="history-table-wrap">
            <table className="history-table">
              <thead><tr><th><SortButton label="Código" column="code" /></th><th><SortButton label="Coleta" column="sampledAt" /></th><th>Localização</th><th><SortButton label="Cultura" column="crop" /></th><th>Profundidade</th><th>Status</th><th>Ações</th></tr></thead>
              <tbody>
                {loading && <tr><td colSpan={7} className="history-empty">Carregando histórico...</td></tr>}
                {!loading && pageItems.map((sample) => (
                  <tr key={sample.id}>
                    <td><strong className="history-code">{sample.code}</strong></td>
                    <td>{formatDate(sample.sampledAt)}</td>
                    <td><strong>{sample.block} · {sample.plot}</strong><small>Linha {sample.row}</small></td>
                    <td>{sample.crop}</td>
                    <td>{sample.depth}</td>
                    <td><span className={`history-status ${sample.analysis ? "done" : "pending"}`}>{sample.analysis ? <CheckCircle2 size={14} /> : <span className="status-dot" />}{sample.analysis ? "Laudo analisado" : "Aguardando laudo"}</span></td>
                    <td><div className="history-actions"><Button variant="ghost" size="sm" title="Visualização rápida" onClick={() => setPreview(sample)}><Eye size={15} /> Ver</Button><Button variant="outline" size="sm" onClick={() => router.push(`/?sample=${sample.id}`)}>Abrir</Button></div></td>
                  </tr>
                ))}
                {!loading && !pageItems.length && <tr><td colSpan={7} className="history-empty"><strong>Nenhuma amostra encontrada.</strong><span>Tente remover alguns filtros ou usar outro termo de busca.</span>{hasFilters && <Button variant="outline" size="sm" onClick={clearFilters}>Limpar filtros</Button>}</td></tr>}
              </tbody>
            </table>
          </div>
          {!loading && filtered.length > 0 && (
            <div className="history-pagination">
              <span>Mostrando <strong>{startItem}–{endItem}</strong> de <strong>{filtered.length}</strong></span>
              <div>
                <Button variant="outline" size="sm" disabled={currentPage === 1} onClick={() => setPage((value) => value - 1)}>Anterior</Button>
                {Array.from({ length: totalPages }, (_, index) => index + 1).slice(Math.max(0, currentPage - 3), Math.min(totalPages, currentPage + 2)).map((item) => <Button key={item} variant={item === currentPage ? "default" : "outline"} size="sm" onClick={() => setPage(item)}>{item}</Button>)}
                <Button variant="outline" size="sm" disabled={currentPage === totalPages} onClick={() => setPage((value) => value + 1)}>Próxima</Button>
              </div>
            </div>
          )}
        </section>
      </section>

      {preview && (
        <div className="history-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setPreview(null); }}>
          <section className="history-modal" role="dialog" aria-modal="true" aria-labelledby="sample-preview-title">
            <div className="history-modal-head"><div><p>VISUALIZAÇÃO RÁPIDA</p><h2 id="sample-preview-title">Amostra {preview.code}</h2></div><Button variant="ghost" size="sm" onClick={() => setPreview(null)} aria-label="Fechar"><X /></Button></div>
            <div className="history-preview-status"><span className={`history-status ${preview.analysis ? "done" : "pending"}`}>{preview.analysis ? <CheckCircle2 size={14} /> : <span className="status-dot" />}{preview.analysis ? "Laudo analisado" : "Aguardando laudo"}</span></div>
            <div className="history-preview-grid">
              <div><span>Data da coleta</span><strong>{formatDate(preview.sampledAt)}</strong></div>
              <div><span>Cultura</span><strong>{preview.crop}</strong></div>
              <div><span>Bloco</span><strong>{preview.block}</strong></div>
              <div><span>Quadra</span><strong>{preview.plot}</strong></div>
              <div><span>Linha</span><strong>{preview.row}</strong></div>
              <div><span>Profundidade</span><strong>{preview.depth}</strong></div>
              <div><span>Temperatura</span><strong>{preview.temperature} °C</strong></div>
            </div>
            <div className="history-modal-actions"><Button variant="outline" onClick={() => setPreview(null)}>Fechar</Button><Button onClick={() => router.push(`/?sample=${preview.id}`)}>Abrir amostra completa</Button></div>
          </section>
        </div>
      )}
    </main>
  );
}
