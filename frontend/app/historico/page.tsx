"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  ArrowUpDown,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  Filter,
  RotateCcw,
  Search,
  X,
} from "lucide-react";
import { useRouter } from "next/navigation";
import { toast, Toaster } from "sonner";
import { useAuth } from "@/context/AuthContext";
import AppHeader from "@/components/AppHeader";
import { ApiError, listSamples } from "@/lib/api";
import { hasSavedAnalysis } from "@/lib/sample-permissions";
import type { Sample } from "@/lib/soil";
import styles from "./historico.module.css";

function formatDate(value: string) {
  return new Date(`${value}T12:00:00`).toLocaleDateString("pt-BR");
}

function messageFromError(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

type SortKey = "sampledAt" | "code" | "crop" | "block";
type SortDirection = "asc" | "desc";

const PAGE_SIZE = 10;

/** Cabeçalho de coluna clicável para ordenação. */
function SortButton({
  label,
  column,
  sortKey,
  sortDirection,
  onSort,
}: {
  label: string;
  column: SortKey;
  sortKey: SortKey;
  sortDirection: SortDirection;
  onSort: (column: SortKey) => void;
}) {
  const active = sortKey === column;

  return (
    <button
      type="button"
      className={`${styles.sortButton} ${active ? styles.sortActive : ""}`}
      onClick={() => onSort(column)}
      title={`Ordenar por ${label}`}
      aria-label={`Ordenar por ${label}${
        active ? (sortDirection === "asc" ? ", ordem crescente" : ", ordem decrescente") : ""
      }`}
    >
      {label}
      {active ? (
        sortDirection === "asc" ? <ArrowUp size={16} /> : <ArrowDown size={16} />
      ) : (
        <ArrowUpDown size={16} className={styles.sortInactive} />
      )}
    </button>
  );
}

/** Indicador de status (aguardando laudo / laudo analisado). */
function StatusBadge({ analyzed }: { analyzed: boolean }) {
  return (
    <span className={`${styles.status} ${analyzed ? styles.statusDone : styles.statusPending}`}>
      {analyzed ? <CheckCircle2 size={14} /> : <span className={styles.statusDot} />}
      {analyzed ? "Laudo analisado" : "Aguardando laudo"}
    </span>
  );
}

export default function HistoricoPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [samples, setSamples] = useState<Sample[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("todos");
  const [crop, setCrop] = useState("todas");
  const [plot, setPlot] = useState("todas");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("sampledAt");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [pagination, setPagination] = useState({ signature: "", page: 1 });
  const filterSignature = JSON.stringify([query, status, crop, plot, fromDate, toDate, sortKey, sortDirection]);
  const [preview, setPreview] = useState<Sample | null>(null);
  const [samplePendingLaudo, setSamplePendingLaudo] = useState<Sample | null>(null);

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


  // Fecha os modais com a tecla Esc.
  useEffect(() => {
    if (!preview && !samplePendingLaudo) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setPreview(null);
        setSamplePendingLaudo(null);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [preview, samplePendingLaudo]);

  const crops = useMemo(
    () => [...new Set(samples.map((sample) => sample.crop))].sort((a, b) => a.localeCompare(b)),
    [samples],
  );
  const plots = useMemo(
    () => [...new Set(samples.map((sample) => sample.plot))].sort((a, b) => a.localeCompare(b)),
    [samples],
  );

  const filtered = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    const result = samples.filter((sample) => {
      const searchable = `${sample.code} ${sample.serviceOrderNumber ?? ""} ${sample.block} ${sample.plot} ${sample.row} ${sample.crop}`.toLowerCase();
      return (
        (!normalizedQuery || searchable.includes(normalizedQuery)) &&
        (status === "todos" || (status === "analisadas" ? hasSavedAnalysis(sample) : !hasSavedAnalysis(sample))) &&
        (crop === "todas" || sample.crop === crop) &&
        (plot === "todas" || sample.plot === plot) &&
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
  }, [samples, query, status, crop, plot, fromDate, toDate, sortKey, sortDirection]);

  const analyzedCount = samples.filter((sample) => sample.analysis).length;
  const pendingCount = samples.length - analyzedCount;
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(pagination.signature === filterSignature ? pagination.page : 1, totalPages);
  function setPage(value: number | ((current: number) => number)) {
    setPagination(previous => ({ signature: filterSignature, page: typeof value === "function" ? value(previous.signature === filterSignature ? previous.page : 1) : value }));
  }
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const startItem = filtered.length ? (currentPage - 1) * PAGE_SIZE + 1 : 0;
  const endItem = Math.min(currentPage * PAGE_SIZE, filtered.length);
  const hasFilters = Boolean(query || status !== "todos" || crop !== "todas" || plot !== "todas" || fromDate || toDate);

  function clearFilters() {
    setQuery("");
    setStatus("todos");
    setCrop("todas");
    setPlot("todas");
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

  function handleOpenSample(sample: Sample) {
    if (hasSavedAnalysis(sample)) {
      // Laudo preenchido: seguir para interpretação.
      router.push(`/?sample=${sample.id}`);
      return;
    }
    // Laudo pendente: solicitar confirmação ao usuário.
    setSamplePendingLaudo(sample);
  }

  function handleRegisterLaudo() {
    if (!samplePendingLaudo) return;
    // Redireciona para a tela principal enviando o ID da amostra para que o laudo seja preenchido lá.
    router.push(`/?sample=${samplePendingLaudo.id}`);
    setSamplePendingLaudo(null);
  }

  if (authLoading || !user) {
    return (
      <main className={`app-shell home-app-shell ${styles.page}`}>
        <div className="flex min-h-screen items-center justify-center">
          <p className="text-sm text-neutral-500">Carregando...</p>
        </div>
      </main>
    );
  }

  const visiblePages = Array.from({ length: totalPages }, (_, index) => index + 1).slice(
    Math.max(0, currentPage - 3),
    Math.min(totalPages, currentPage + 2),
  );

  return (
    <main className={`app-shell home-app-shell ${styles.page}`}>
      <Toaster position="top-right" richColors />
      <AppHeader />

      <div className={styles.content}>
        {/* Cabeçalho da página */}
        <div className={styles.pageHeading}>
          <div className={styles.pageHeadingCopy}>
            <p className={styles.eyebrow}>GESTÃO DE FERTILIDADE</p>
            <h1>Histórico de amostras</h1>
            <p className={styles.pageDescription}>
              Consulte, filtre e encontre rapidamente as amostragens registradas no sistema.
            </p>
          </div>
          <button type="button" className={styles.backButton} onClick={() => router.push("/")}>
            Voltar
          </button>
        </div>

        {/* Resumo */}
        <section className={`${styles.card} ${styles.summary}`} aria-label="Resumo das amostras">
          <div className={styles.summaryItem}>
            <span>Total de amostras</span>
            <strong>{samples.length}</strong>
            <small>registros cadastrados</small>
          </div>
          <div className={styles.summaryItem}>
            <span>Laudos analisados</span>
            <strong>{analyzedCount}</strong>
            <small>com resultados laboratoriais</small>
          </div>
          <div className={styles.summaryItem}>
            <span>Aguardando laudo</span>
            <strong>{pendingCount}</strong>
            <small>sem análise registrada</small>
          </div>
        </section>

        {/* Filtros */}
        <section className={`${styles.card} ${styles.filterCard}`} aria-labelledby="filter-title">
          <div className={styles.cardHeading}>
            <span className={styles.sectionIcon} aria-hidden="true">
              <Filter size={22} strokeWidth={2.2} />
            </span>
            <h2 id="filter-title" className={styles.cardTitle}>Filtrar amostras</h2>
            {hasFilters && (
              <button type="button" className={styles.clearButton} onClick={clearFilters}>
                <RotateCcw size={14} /> Limpar filtros
              </button>
            )}
          </div>

          <div className={styles.filters}>
            <div className={`${styles.field} ${styles.fieldSearch}`}>
              <label htmlFor="search-filter">Pesquisar</label>
              <div className={styles.inputWrap}>
                <Search size={20} className={styles.inputIcon} />
                <input
                  id="search-filter"
                  className={`${styles.control} ${styles.controlWithIcon}`}
                  placeholder="Código, OS, bloco, quadra, linha ou cultura"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </div>
            </div>

            <div className={`${styles.field} ${styles.fieldStatus}`}>
              <label htmlFor="status-filter">Status</label>
              <div className={styles.inputWrap}>
                <select
                  id="status-filter"
                  className={`${styles.control} ${styles.select}`}
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                >
                  <option value="todos">Todos</option>
                  <option value="analisadas">Laudo analisado</option>
                  <option value="pendentes">Aguardando laudo</option>
                </select>
                <ChevronDown size={20} className={styles.selectIcon} />
              </div>
            </div>

            <div className={styles.field}>
              <label htmlFor="crop-filter">Cultura</label>
              <div className={styles.inputWrap}>
                <select
                  id="crop-filter"
                  className={`${styles.control} ${styles.select}`}
                  value={crop}
                  onChange={(event) => setCrop(event.target.value)}
                >
                  <option value="todas">Todas</option>
                  {crops.map((item) => (
                    <option key={item} value={item}>{item}</option>
                  ))}
                </select>
                <ChevronDown size={20} className={styles.selectIcon} />
              </div>
            </div>

            <div className={styles.field}>
              <label htmlFor="plot-filter">Quadra</label>
              <div className={styles.inputWrap}>
                <select
                  id="plot-filter"
                  className={`${styles.control} ${styles.select}`}
                  value={plot}
                  onChange={(event) => setPlot(event.target.value)}
                >
                  <option value="todas">Todas</option>
                  {plots.map((item) => (
                    <option key={item} value={item}>Quadra {item}</option>
                  ))}
                </select>
                <ChevronDown size={20} className={styles.selectIcon} />
              </div>
            </div>

            <div className={styles.field}>
              <label htmlFor="from-date">Coleta a partir de</label>
              <div className={styles.inputWrap}>
                <CalendarDays size={20} className={styles.inputIcon} />
                <input
                  id="from-date"
                  type="date"
                  className={`${styles.control} ${styles.controlWithIcon}`}
                  value={fromDate}
                  onChange={(event) => setFromDate(event.target.value)}
                />
              </div>
            </div>

            <div className={styles.field}>
              <label htmlFor="to-date">Coleta até</label>
              <div className={styles.inputWrap}>
                <CalendarDays size={20} className={styles.inputIcon} />
                <input
                  id="to-date"
                  type="date"
                  className={`${styles.control} ${styles.controlWithIcon}`}
                  value={toDate}
                  onChange={(event) => setToDate(event.target.value)}
                />
              </div>
            </div>
          </div>
        </section>

        {/* Resultados */}
        <section className={`${styles.card} ${styles.resultsCard}`} aria-labelledby="results-title">
          <div className={styles.resultsHeading}>
            <div>
              <h2 id="results-title" className={styles.cardTitle}>Amostras Encontradas</h2>
              <span className={styles.resultsCount}>
                {startItem}–{endItem} de {filtered.length} registros
              </span>
            </div>
            <div className={styles.resultsTools}>
              {hasFilters && <span className={styles.filterActive}>Filtro ativo</span>}
              <span className={styles.sortHint}>Clique no cabeçalho para ordenar</span>
            </div>
          </div>

          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th><SortButton label="Código" column="code" sortKey={sortKey} sortDirection={sortDirection} onSort={changeSort} /></th>
                  <th><SortButton label="Coleta" column="sampledAt" sortKey={sortKey} sortDirection={sortDirection} onSort={changeSort} /></th>
                  <th>Localização</th>
                  <th><SortButton label="Cultura" column="crop" sortKey={sortKey} sortDirection={sortDirection} onSort={changeSort} /></th>
                  <th>Profundidade</th>
                  <th>Status</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr><td colSpan={7} className={styles.empty}>Carregando histórico...</td></tr>
                )}
                {!loading && pageItems.map((sample) => (
                  <tr key={sample.id}>
                    <td><strong className={styles.code}>{sample.code}</strong>{sample.serviceOrderNumber && <small className={styles.serviceOrder}>OS-{sample.serviceOrderNumber}</small>}</td>
                    <td>{formatDate(sample.sampledAt)}</td>
                    <td>
                      <span className={styles.location}>
                        <strong>{sample.block} · {sample.plot}</strong>
                        <small>Linha {sample.row}</small>
                      </span>
                    </td>
                    <td>{sample.crop}</td>
                    <td>{sample.depth}</td>
                    <td><StatusBadge analyzed={hasSavedAnalysis(sample)} /></td>
                    <td>
                      <div className={styles.actions}>
                        <button
                          type="button"
                          className={styles.actionButton}
                          title="Visualização rápida"
                          onClick={() => setPreview(sample)}
                        >
                          Ver
                        </button>
                        <button
                          type="button"
                          className={styles.actionButton}
                          onClick={() => handleOpenSample(sample)}
                        >
                          Abrir
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && !pageItems.length && (
                  <tr>
                    <td colSpan={7} className={styles.empty}>
                      <strong>Nenhuma amostra encontrada.</strong>
                      <span>Tente remover alguns filtros ou usar outro termo de busca.</span>
                      {hasFilters && (
                        <button type="button" className={styles.actionButton} onClick={clearFilters}>
                          Limpar filtros
                        </button>
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {!loading && filtered.length > 0 && (
            <div className={styles.pagination}>
              <span>Mostrando {startItem}–{endItem} de {filtered.length}</span>
              <div className={styles.pager}>
                <button
                  type="button"
                  className={styles.pagerArrow}
                  disabled={currentPage === 1}
                  onClick={() => setPage((value) => value - 1)}
                  aria-label="Página anterior"
                >
                  <ArrowLeft size={22} />
                </button>
                {visiblePages.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`${styles.pagerPage} ${item === currentPage ? styles.pagerPageActive : ""}`}
                    onClick={() => setPage(item)}
                    aria-current={item === currentPage ? "page" : undefined}
                  >
                    {item}
                  </button>
                ))}
                <button
                  type="button"
                  className={styles.pagerArrow}
                  disabled={currentPage === totalPages}
                  onClick={() => setPage((value) => value + 1)}
                  aria-label="Próxima página"
                >
                  <ArrowRight size={22} />
                </button>
              </div>
            </div>
          )}
        </section>
      </div>

      {/* Modal: visualização rápida */}
      {preview && (
        <div
          className={styles.backdrop}
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setPreview(null);
          }}
        >
          <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="sample-preview-title">
            <div className={styles.modalHead}>
              <div>
                <p>VISUALIZAÇÃO RÁPIDA</p>
                <h2 id="sample-preview-title">Amostra {preview.code}</h2>
              </div>
              <button type="button" className={styles.closeButton} onClick={() => setPreview(null)} aria-label="Fechar">
                <X size={22} />
              </button>
            </div>

            <div className={styles.modalBody}>
              <div className={styles.previewStatus}>
                <StatusBadge analyzed={hasSavedAnalysis(preview)} />
              </div>
              <div className={styles.previewGrid}>
                <div><span>Data da coleta</span><strong>{formatDate(preview.sampledAt)}</strong></div>
                <div><span>Cultura</span><strong>{preview.crop}</strong></div>
                <div><span>Bloco</span><strong>{preview.block}</strong></div>
                <div><span>Quadra</span><strong>{preview.plot}</strong></div>
                <div><span>Linha</span><strong>{preview.row}</strong></div>
                <div><span>Profundidade</span><strong>{preview.depth}</strong></div>
                <div><span>Temperatura</span><strong>{preview.temperature} °C</strong></div>
              </div>
            </div>

            <div className={styles.modalActions}>
              <button type="button" className={styles.secondaryButton} onClick={() => setPreview(null)}>
                Fechar
              </button>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={() => {
                  setPreview(null);
                  router.push(
                    `/?sample=${encodeURIComponent(preview.id)}&view=${hasSavedAnalysis(preview) ? "relatorio" : "analise"}`,
                  );
                }}
              >
                Abrir amostra completa
              </button>
            </div>
          </section>
        </div>
      )}

      {/* Modal: laudo pendente */}
      {samplePendingLaudo && (
        <div
          className={styles.backdrop}
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setSamplePendingLaudo(null);
          }}
        >
          <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="laudo-modal-title">
            <div className={styles.modalHead}>
              <div>
                <p>LAUDO PENDENTE</p>
                <h2 id="laudo-modal-title">Registro de laudo necessário</h2>
              </div>
              <button type="button" className={styles.closeButton} onClick={() => setSamplePendingLaudo(null)} aria-label="Fechar">
                <X size={22} />
              </button>
            </div>

            <div className={`${styles.modalBody} ${styles.modalText}`}>
              <p>
                A amostra <strong>{samplePendingLaudo.code}</strong> ainda não possui um laudo registrado.
              </p>
              <p>
                Para continuar para a tela de interpretação, é necessário realizar o registro do laudo primeiro.
              </p>
              <p>Deseja registrar o laudo agora?</p>
            </div>

            <div className={styles.modalActions}>
              <button type="button" className={styles.secondaryButton} onClick={() => setSamplePendingLaudo(null)}>
                Continuar no histórico
              </button>
              <button type="button" className={styles.primaryButton} onClick={handleRegisterLaudo}>
                Registrar laudo
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
