"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ClipboardPlus,
  FlaskConical,
  Globe,
  HelpCircle,
  Printer,
  Search,
} from "lucide-react";
import { toast, Toaster } from "sonner";
import { useRouter, useSearchParams } from "next/navigation"; // NOVO (Fase 4)
import { useAuth } from "@/context/AuthContext"; // NOVO (Fase 4)
import { analysisDraft, buildAnalysisPayload } from "@/lib/analysis";
import { hasSavedAnalysis, canWriteAnalysis } from "@/lib/sample-permissions";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from "@/components/ui/dialog";
import { boundedDecimal, businessDate } from "@/lib/validation";
import AppHeader from "@/components/AppHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import {
  ApiError,
  createSample as createSampleApi,
  getHealth,
  getSample,
  updateSample,
  getInterpretation,
  listSamples,
  request,
  updateAnalysis,
} from "@/lib/api";
import {
  ANALYSIS_FIELDS,
  analysisUnitOptions,
  type Sample,
  type SoilAnalysis,
  type SoilReport,
} from "@/lib/soil";

const makeEmptyMeta = () => ({
  serviceOrderNumber: "",
  block: "",
  plot: "",
  row: "",
  crop: "",
  sampledAt: businessDate(),
  temperature: "",
  observations: "",
  depth: "0–20 cm",
});

function metaFromSample(sample: Sample) {
  return { serviceOrderNumber: sample.serviceOrderNumber ?? "", block: sample.block, plot: sample.plot, row: sample.row,
    crop: sample.crop, sampledAt: sample.sampledAt, temperature: sample.temperature ?? "", observations: sample.observations ?? "", depth: sample.depth };
}

const makeEmptyAnalysis = () => analysisDraft();

function messageFromError(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function StatusPill({ level }: { level: string }) {
  const slug = level
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replaceAll(" ", "-");

  return <span className={`status status-${slug}`}>{level}</span>;
}

export default function HomePage() {
  return <Suspense fallback={<main className="flex min-h-screen items-center justify-center">Carregando...</main>}><Home /></Suspense>;
}

function Home() {
  // NOVO (Fase 4): pega o usuário logado e a função de logout do contexto
  const { user, loading: authLoading, refreshUser } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const sampleIdParam = searchParams.get("sample");

  const [samples, setSamples] = useState<Sample[]>([]);
  const [cultures, setCultures] = useState<string[]>([]);
  const [culturesLoading, setCulturesLoading] = useState(true);
  const [culturesError, setCulturesError] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [meta, setMeta] = useState(makeEmptyMeta);
  const [analysis, setAnalysis] = useState<SoilAnalysis>(makeEmptyAnalysis);
  const [report, setReport] = useState<SoilReport | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState("cadastro");
  const [online, setOnline] = useState(false);
  const [loadingSamples, setLoadingSamples] = useState(true);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savingMeta, setSavingMeta] = useState(false);
  const [editingMeta, setEditingMeta] = useState(false);
  const [opening, setOpening] = useState(false);
  const [openFailed, setOpenFailed] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [draftVersion, setDraftVersion] = useState<Sample["recordVersion"]>("");
  const [confirmation, setConfirmation] = useState<"first" | "reload" | null>(null);
  const [pendingAnalysis, setPendingAnalysis] = useState<SoilAnalysis | null>(null);
  const selectionRequest = useRef(0);
  const [formError, setFormError] = useState("");
  const reportRequest = useRef(0);
  const selectedRef = useRef<string | null>(null);
  const busyRef = useRef(false);

  // NOVO (Fase 4): se não estiver logado (e já terminou de checar), manda pro /login
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/login");
    }
  }, [authLoading, user, router]);

  const selected = samples.find((sample) => sample.id === selectedId) ?? null;
  const savedAnalysis = hasSavedAnalysis(selected);
  const analysisWritable = canWriteAnalysis(user?.role, selected);
  const writing = creating || saving || savingMeta;
  const fieldsBlocked = writing || opening || openFailed || conflict;
  const filtered = useMemo(
    () =>
      samples.filter((sample) =>
        `${sample.code} ${sample.serviceOrderNumber ?? ""} ${sample.block} ${sample.plot} ${sample.crop}`
          .toLowerCase()
          .includes(query.toLowerCase()),
      ),
    [samples, query],
  );

  useEffect(() => {
    if (authLoading || !user) return;
    let cancelled = false;

    request<string[]>("/interpretation-parameters/cultures")
      .then((items) => {
        if (!cancelled) {
          setCultures(items);
          setCulturesError(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCultures([]);
          setCulturesError(true);
        }
      })
      .finally(() => {
        if (!cancelled) setCulturesLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, user]);

  useEffect(() => {
    if (authLoading || !user) return;
    let cancelled = false;
    async function bootstrap() {
      try {
        await getHealth();
        const data = await listSamples();
        if (!cancelled) { setSamples(data); setOnline(true); }
      } catch (error) {
        if (!cancelled) { setOnline(false); setFormError(messageFromError(error, "Não foi possível carregar as amostras.")); }
      } finally {
        if (!cancelled) setLoadingSamples(false);
      }
    }

    void bootstrap();
    return () => { cancelled = true; };
  }, [authLoading, user]);

  const loadReport = useCallback(async (sampleId: string) => {
    const ticket = ++reportRequest.current;
    setReportLoading(true);
    try {
      const data = await getInterpretation(sampleId);
      if (ticket !== reportRequest.current) return null;
      setReport(data);
      return data;
    } catch (error) {
      if (ticket === reportRequest.current) {
        setReport(null);
        toast.error(messageFromError(error, "Não foi possível carregar a interpretação."));
      }
      return null;
    } finally {
      if (ticket === reportRequest.current) setReportLoading(false);
    }
  }, []);

  async function createSample(event: React.FormEvent) {
    event.preventDefault();
    if (busyRef.current) return;
    setFormError("");

    // A cultura precisa existir no catálogo persistido, carregado de Parâmetros.
    if (culturesLoading) {
      toast.error("Aguarde o carregamento das culturas cadastradas.");
      return;
    }
    if (culturesError) {
      toast.error("Não foi possível consultar as culturas cadastradas. Tente novamente.");
      return;
    }
    if (!meta.crop || !cultures.includes(meta.crop)) {
      toast.error("Selecione uma cultura cadastrada e salva na tela Parâmetros.");
      return;
    }

    try {
      const temperature = boundedDecimal(meta.temperature, "Temperatura", { min: "-20", max: "80", integer: 2, fraction: 2 });
      busyRef.current = true;
      setCreating(true);
      const sample = await createSampleApi({
        ...meta,
        temperature,
      });
      setSamples((current) => [sample, ...current]);
      selectedRef.current = sample.id;
      reportRequest.current++;
      setSelectedId(sample.id);
      router.replace(`/?sample=${sample.id}`);
      setAnalysis(makeEmptyAnalysis());
      setReport(null);
      setMeta(metaFromSample(sample)); setDraftVersion(sample.recordVersion);
      setEditingMeta(false); setConflict(false); setOpenFailed(false);
      setOnline(true);
      setTab("analise");
      toast.success(`Amostragem ${sample.code} cadastrada`);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 0 || error.status >= 500)) setOnline(false);
      setFormError(messageFromError(error, "Não foi possível cadastrar a amostra."));
      toast.error(
        messageFromError(
          error,
          "Não foi possível cadastrar a amostra. Tente novamente.",
        ),
      );
    } finally { busyRef.current = false; setCreating(false); }
  }

  function mutationError(error: unknown, fallback: string) {
    let message = messageFromError(error, fallback);
    if (error instanceof ApiError && (error.status === 409 || error.status === 428)) {
      setConflict(true);
      message = `${message} Seu rascunho continua nesta tela. Reabra o registro para conferir a versão atual; ele não será sobrescrito automaticamente.`;
    }
    if (error instanceof ApiError && error.status === 403) void refreshUser().catch(() => {});
    setFormError(message); toast.error(message);
  }

  async function saveAnalysis(event: React.FormEvent) {
    event.preventDefault();
    if (!selected || !analysisWritable || fieldsBlocked || busyRef.current) return;
    setFormError("");
    try {
      const numeric = buildAnalysisPayload(analysis);
      if (user?.role === "USER" && !hasSavedAnalysis(selected)) {
        setPendingAnalysis(numeric); setConfirmation("first"); return;
      }
      await performAnalysisSave(numeric);
    } catch (error) { mutationError(error,"Não foi possível salvar o laudo."); }
  }

  async function performAnalysisSave(payload: SoilAnalysis) {
    if (!selected || !analysisWritable || fieldsBlocked || busyRef.current) return;
    const target = selected;
    busyRef.current = true; setSaving(true); setConfirmation(null); setPendingAnalysis(null);
    try {
      const updated = await updateAnalysis(target.id, payload, draftVersion);
      setSamples(current => current.map(sample => sample.id === target.id ? updated : sample));
      setAnalysis(analysisDraft(updated.analysis)); setDraftVersion(updated.recordVersion); setOnline(true);
      const interpreted = await loadReport(target.id);
      if (interpreted) { setTab("relatorio"); toast.success("Laudo salvo e interpretação atualizada."); }
    } catch (error) { mutationError(error,"Não foi possível salvar o laudo."); }
    finally { busyRef.current = false; setSaving(false); }
  }

  async function saveMetadata(event: React.FormEvent) {
    event.preventDefault();
    if (!selected || user?.role !== "ADMIN" || !editingMeta || fieldsBlocked || busyRef.current) return;
    setFormError("");
    try {
      if (!cultures.includes(meta.crop)) throw new Error("Selecione uma cultura cadastrada em Parâmetros.");
      const temperature = boundedDecimal(meta.temperature,"Temperatura",{min:"-20",max:"80",integer:2,fraction:2});
      busyRef.current = true; setSavingMeta(true);
      const updated = await updateSample(selected.id,{...meta,temperature},draftVersion);
      setSamples(current => current.map(sample => sample.id === updated.id ? updated : sample));
      setMeta(metaFromSample(updated)); setDraftVersion(updated.recordVersion); setEditingMeta(false); setReport(null);
      toast.success("Dados da amostra salvos.");
      if (hasSavedAnalysis(updated)) await loadReport(updated.id);
    } catch (error) { mutationError(error,"Não foi possível atualizar a amostra."); }
    finally { busyRef.current = false; setSavingMeta(false); }
  }

  const chooseSample = useCallback(async (sample: Sample) => {
    if (busyRef.current) return;
    const ticket = ++selectionRequest.current;
    selectedRef.current = sample.id; reportRequest.current++;
    setReportLoading(false); setFormError(""); setSelectedId(sample.id); setEditingMeta(false);
    setConflict(false); setOpenFailed(false); setOpening(true); setReport(null); setConfirmation(null); setPendingAnalysis(null);
    setDraftVersion("");
    setMeta(metaFromSample(sample)); setAnalysis(analysisDraft(sample.analysis));
    if (new URLSearchParams(window.location.search).get("sample") !== sample.id) router.replace(`/?sample=${sample.id}`);
    try {
      const fresh = await getSample(sample.id);
      if (ticket !== selectionRequest.current) return;
      setSamples(current => current.map(item => item.id === fresh.id ? fresh : item));
      setDraftVersion(fresh.recordVersion);
      setMeta(metaFromSample(fresh)); setAnalysis(analysisDraft(fresh.analysis));
      const requestedView = new URLSearchParams(window.location.search).get("view");
      setTab(requestedView === "cadastro" || requestedView === "analise" ? requestedView : hasSavedAnalysis(fresh) ? "relatorio" : "analise");
      if (hasSavedAnalysis(fresh)) await loadReport(fresh.id);
    } catch (error) {
      if (ticket === selectionRequest.current) { setOpenFailed(true); setFormError(messageFromError(error,"Não foi possível reabrir a amostra.")); }
    } finally { if (ticket === selectionRequest.current) setOpening(false); }
  }, [loadReport, router]);

  useEffect(() => {
    if (!sampleIdParam || samples.length === 0 || selectedRef.current === sampleIdParam) return;
    const target = samples.find(sample => sample.id === sampleIdParam);
    if (target) void Promise.resolve().then(() => chooseSample(target));
  }, [sampleIdParam, samples, chooseSample, saving, savingMeta, creating]);

  function startNewSample() {
    if (busyRef.current) return;
    selectionRequest.current++;
    setOpening(false); setOpenFailed(false); setConflict(false); setEditingMeta(false); setConfirmation(null); setPendingAnalysis(null);
    selectedRef.current = null;
    reportRequest.current++;
    setReportLoading(false); setFormError("");
    if (sampleIdParam) router.replace("/");
    setSelectedId(null);
    setAnalysis(makeEmptyAnalysis());
    setReport(null);
    setMeta(makeEmptyMeta());
    setTab("cadastro");
  }

  // NOVO (Fase 4): enquanto não sabemos se há usuário, ou se não há usuário,
  // mostra uma tela simples em vez da tela real (o useEffect acima já está
  // redirecionando pro /login nesse caso)
  if (authLoading || !user) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-neutral-500">Carregando...</p>
      </main>
    );
  }

  return (
    <main className="app-shell home-app-shell">
      <Toaster position="top-right" richColors />

      <AppHeader />

      <div className="workspace home-workspace">
        <aside className="sidebar-panel home-sidebar-panel no-print">
          <Button className="new-button home-new-button" onClick={startNewSample}>
            <ClipboardPlus size={19} /> Nova amostragem
          </Button>

          <div className="search-box">
            <Search size={17} />
            <Input
              aria-label="Buscar amostragem"
              placeholder="Buscar código, bloco..."
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </div>

          <div className="list-heading">
            <span>Amostragens</span>
            <small>{filtered.length}</small>
          </div>

          <div className="sample-list">
            {loadingSamples && <p className="empty-copy">Carregando amostragens...</p>}

            {!loadingSamples &&
              filtered.map((sample) => (
                <button
                  key={sample.id}
                  disabled={writing}
                  onClick={() => void chooseSample(sample)}
                  className={`sample-card ${selectedId === sample.id ? "selected" : ""}`}
                >
                  <span className="sample-card-heading">
                    <span className="sample-code">{sample.code}</span>
                    {sample.serviceOrderNumber && (
                      <span className="sample-order">OS-{sample.serviceOrderNumber}</span>
                    )}
                  </span>
                  <strong>
                    {sample.block} · {sample.plot}
                  </strong>
                  <span>
                    {sample.crop} ·{" "}
                    {new Date(`${sample.sampledAt}T12:00:00`).toLocaleDateString("pt-BR")}
                  </span>
                  <i className={hasSavedAnalysis(sample) ? "done" : "pending"}>
                    {hasSavedAnalysis(sample) ? "Laudo analisado" : "Aguardando laudo"}
                  </i>
                </button>
              ))}

            {!loadingSamples && !filtered.length && (
              <p className="empty-copy">Nenhuma amostragem cadastrada.</p>
            )}
          </div>

          <p className={`connection ${online ? "online" : "offline"}`}>
            {online ? "Servidor conectado" : "Servidor indisponível"}
          </p>
        </aside>

        <section className="content-panel home-content-panel">
          <div className="page-intro no-print ds-page-heading">
            <p>GESTÃO DE FERTILIDADE</p>
            <h1>
              {selected
                ? `${selected.code}${selected.serviceOrderNumber ? ` · OS-${selected.serviceOrderNumber}` : ""}`
                : "Nova Amostragem"}
            </h1>
            <span>
              {selected
                ? `${selected.crop} · Bloco ${selected.block} · Quadra ${selected.plot}`
                : "Registre a coleta, receba o laudo e gere a interpretação."}
            </span>
          </div>

          {formError && <p role="alert" className="form-error">{formError}</p>}
          {opening && <p role="status">Reabrindo a versão atual da amostra…</p>}
          {selected && (conflict || openFailed) && <Button type="button" variant="outline" onClick={() => setConfirmation("reload")}>Reabrir registro atualizado</Button>}
          {selected && user.role === "ADMIN" && <div className="no-print" style={{marginTop:12}}><Button type="button" variant="outline" onClick={() => router.push(`/admin/auditoria?sample=${encodeURIComponent(selected.id)}`)}>Auditoria desta amostra</Button></div>}
          <Tabs value={tab} onValueChange={setTab} className="workflow">
            <TabsList className="steps no-print">
              <TabsTrigger value="cadastro">
                <b>01</b> Amostragem
              </TabsTrigger>
              <TabsTrigger value="analise" disabled={!selected}>
                <b>02</b> Laudo
              </TabsTrigger>
              <TabsTrigger value="relatorio" disabled={!savedAnalysis}>
                <b>03</b> Interpretação
              </TabsTrigger>
            </TabsList>

            <TabsContent value="cadastro" className="paper ds-panel">
              <div className="section-title">
                <span className="home-section-icon"><Globe size={21} /></span>
                <div>
                  <h2>Dados da coleta</h2>
                  <p>Identificação e condições da amostragem em campo.</p>
                </div>
              </div>

              {selected && <div className="record-permissions">
                <p>{editingMeta ? "Editando os dados da amostra." : "Dados cadastrados da amostra."} {!selected.createdBy && "Autoria não registrada."}</p>
                {user.role === "ADMIN" && !editingMeta && <Button type="button" variant="outline" disabled={fieldsBlocked} onClick={() => setEditingMeta(true)}>Editar dados da amostra</Button>}
                {user.role !== "ADMIN" && <p>Modo de consulta. Somente um administrador pode alterar estes dados.</p>}
              </div>}
              <form onSubmit={selected ? saveMetadata : createSample}>
              <fieldset className="form-grid metadata-fields" disabled={fieldsBlocked || Boolean(selected && !editingMeta)}>
                <div className="field field-serviceOrderNumber">
                  <div className="field-label-with-help">
                    <Label htmlFor="serviceOrderNumber">Número da OS</Label>
                    <span
                      className="field-help-trigger"
                      tabIndex={0}
                      aria-label="Ajuda sobre o número da OS"
                      aria-describedby="service-order-help"
                    >
                      <HelpCircle size={15} aria-hidden="true" />
                      <span id="service-order-help" className="field-tooltip" role="tooltip">
                        Informe os 8 dígitos da ordem de serviço, incluindo zeros à esquerda.
                      </span>
                    </span>
                  </div>
                  <Input id="serviceOrderNumber" name="serviceOrderNumber" type="text"
                    inputMode="numeric" pattern="[0-9]{8}" minLength={8} maxLength={8}
                    required placeholder="Ex.: 00123456" value={meta.serviceOrderNumber}
                    aria-describedby="service-order-help"
                    onChange={(event) => setMeta({ ...meta, serviceOrderNumber: event.target.value })} />
                </div>
                {[
                  ["Bloco", "block", "Ex.: C"],
                  ["Quadra", "plot", "Ex.: Q12"],
                  ["Linha", "row", "Ex.: L08"],
                  ["Cultura", "crop", "Escolha uma cultura"],
                  ["Data da coleta", "sampledAt", ""],
                  ["Temperatura (°C)", "temperature", "Ex.: 27"],
                ].map(([label, key, placeholder]) => (
                  <div className={`field field-${key}`} key={key}>
                    <Label htmlFor={key}>{label}</Label>
                    {key === "crop" ? (
                      <NativeSelect
                        id="crop"
                        value={meta.crop}
                        required
                        disabled={culturesLoading || culturesError || cultures.length === 0}
                        onChange={(event) => setMeta({ ...meta, crop: event.target.value })}
                      >
                        <NativeSelectOption value="">
                          {culturesLoading
                            ? "Carregando culturas..."
                            : culturesError
                              ? "Não foi possível carregar as culturas"
                              : cultures.length > 0
                                ? "Escolha uma cultura"
                                : "Cadastre uma cultura em Parâmetros"}
                        </NativeSelectOption>
                        {selected && meta.crop && !cultures.includes(meta.crop) && <NativeSelectOption value={meta.crop}>{meta.crop} (histórico)</NativeSelectOption>}
                        {cultures.map((culture) => (
                          <NativeSelectOption key={culture} value={culture}>
                            {culture}
                          </NativeSelectOption>
                        ))}
                      </NativeSelect>
                    ) : (
                      <Input
                        id={key}
                        type={
                          key === "sampledAt"
                            ? "date"
                            : key === "temperature"
                              ? "number"
                              : "text"
                        }
                        step={key === "temperature" ? "0.01" : undefined}
                        min={key === "temperature" ? -20 : undefined}
                        max={key === "temperature" ? 80 : undefined}
                        maxLength={["block", "plot", "row"].includes(key) ? 80 : undefined}
                        required
                        value={(meta as Record<string, string>)[key]}
                        placeholder={placeholder}
                        onChange={(event) =>
                          setMeta({ ...meta, [key]: event.target.value })
                        }
                      />
                    )}
                  </div>
                ))}

                <div className="field field-depth">
                  <Label htmlFor="depth">Profundidade</Label>
                  <NativeSelect
                    id="depth"
                    value={meta.depth}
                    onChange={(event) => setMeta({ ...meta, depth: event.target.value })}
                  >
                    {!["0–20 cm","20–40 cm","40–60 cm"].includes(meta.depth) && <NativeSelectOption value={meta.depth}>{meta.depth}</NativeSelectOption>}
                    <NativeSelectOption value="0–20 cm">0–20 cm</NativeSelectOption>
                    <NativeSelectOption value="20–40 cm">20–40 cm</NativeSelectOption>
                    <NativeSelectOption value="40–60 cm">40–60 cm</NativeSelectOption>
                  </NativeSelect>
                </div>

                <div className="field sample-observations">
                  <Label htmlFor="observations">Observações</Label>
                  <Textarea
                    id="observations"
                    name="observations"
                    rows={3}
                    placeholder="Informações adicionais sobre a coleta (opcional)"
                    value={meta.observations}
                    onChange={(event) => setMeta({ ...meta, observations: event.target.value })}
                  />
                </div>

                <div className="form-actions sampling-actions">
                  {(!selected || editingMeta) && <>
                    {selected && <Button type="button" variant="outline" onClick={() => { setMeta(metaFromSample(selected)); setEditingMeta(false); setFormError(""); }}>Cancelar edição</Button>}
                    <Button type="submit" disabled={writing || culturesLoading || culturesError || cultures.length === 0}>{writing ? "Salvando..." : selected ? "Salvar dados da amostra" : "Gerar amostra e salvar"}</Button>
                  </>}
                </div>
              </fieldset>
              </form>
            </TabsContent>

            <TabsContent value="analise" className="paper ds-panel">
              <div className="section-title">
                <span className="home-section-icon"><FlaskConical size={21} /></span>
                <div>
                  <h2>Resultados laboratoriais</h2>
                  <p>Informe valores e unidades exatamente conforme o método indicado.</p>
                </div>
              </div>

              {selected && savedAnalysis && !selected.analysisCreatedBy && <p className="method-note">Autoria inicial do laudo não registrada.</p>}
              {!analysisWritable && <p className="permission-note">Laudo registrado — modo de consulta. Somente um administrador pode corrigir ou complementar os resultados.</p>}
              {analysisWritable && user.role === "USER" && <p className="permission-note">Você pode registrar o primeiro laudo. Após salvar, inclusive com campos em branco, somente um administrador poderá alterar os resultados.</p>}
              <form onSubmit={saveAnalysis}>
                {selected?.analysis?.phCaCl2 == null && selected?.analysis?.phWater != null && (
                  <p className="method-note">pH em água do laudo original: {String(selected.analysis.phWater)}. O valor será preservado enquanto o pH em CaCl₂ permanecer em branco.</p>
                )}
                <div className="analysis-grid">
                  {ANALYSIS_FIELDS.map((field) => (
                    <div className="analysis-field" key={field.key}>
                      <Label htmlFor={field.key}>{field.label}</Label>
                      <div>
                        <Input
                          id={field.key}
                          type="number"
                          step="any"
                          min="0"
                          max={field.key === "phCaCl2" ? 14 : field.key === "baseSatReported" ? 100 : field.unit === "g/kg" ? (analysis.units?.[field.key] === "%" ? 100 : 1000) : undefined}
                          disabled={fieldsBlocked || !analysisWritable}
                          value={(analysis[field.key] ?? "") as string | number}
                          onChange={(event) =>
                            setAnalysis({
                              ...analysis,
                              [field.key]: event.target.value,
                            })
                          }
                        />
                        {analysisUnitOptions(field.unit).length > 0 ? (
                          <NativeSelect
                            aria-label={`Unidade de ${field.label}`}
                            disabled={fieldsBlocked || !analysisWritable}
                            value={analysis.units?.[field.key] ?? field.unit}
                            onChange={(event) => setAnalysis({
                              ...analysis,
                              units: { ...analysis.units, [field.key]: event.target.value },
                            })}
                          >
                            {analysisUnitOptions(field.unit).map((unit) => (
                              <NativeSelectOption key={unit} value={unit}>{unit}</NativeSelectOption>
                            ))}
                          </NativeSelect>
                        ) : <span>{field.unit}</span>}
                      </div>
                    </div>
                  ))}

                  <div className="analysis-field analysis-field-phosphorus-method">
                    <Label htmlFor="phosphorusMethod">Extrator de fósforo</Label>
                    <div>
                      <NativeSelect disabled={fieldsBlocked || !analysisWritable} id="phosphorusMethod" value={analysis.phosphorusMethod ?? "Não informado"}
                        onChange={(event) => setAnalysis({ ...analysis, phosphorusMethod: event.target.value })}>
                        {["Resina", "Mehlich 1", "Não informado"].map((method) => (
                          <NativeSelectOption key={method} value={method}>{method}</NativeSelectOption>
                        ))}
                      </NativeSelect>
                    </div>
                  </div>
                  <div className="analysis-field analysis-field-density">
                    <div className="field-label-with-help">
                      <Label htmlFor="density">Densidade de Conversão (kg/dm³)</Label>
                      <span
                        className="field-help-trigger"
                        tabIndex={0}
                        aria-label="Ajuda sobre a densidade de conversão"
                        aria-describedby="density-help"
                      >
                        <HelpCircle size={15} aria-hidden="true" />
                        <span id="density-help" className="field-tooltip" role="tooltip">
                          Necessária para converter g/kg ou mg/kg em g/dm³. Use a relação massa/volume validada para o método do laboratório.
                        </span>
                      </span>
                    </div>
                    <div>
                      <Input disabled={fieldsBlocked || !analysisWritable} id="density" type="number" min="0" step="any"
                        value={analysis.density ?? ""}
                        onChange={(event) => setAnalysis({ ...analysis, density: event.target.value })}
                        aria-describedby="density-help" />
                    </div>
                  </div>

                  <div className="form-actions analysis-actions">
                    <Button className="analysis-back-button" variant="outline" type="button" onClick={() => setTab("cadastro")}>
                      Voltar
                    </Button>
                    {analysisWritable && <Button type="submit" disabled={fieldsBlocked}>{saving ? "Salvando..." : "Salvar e interpretar"}</Button>}
                  </div>
                </div>

                <div className="method-note">
                  <strong>Métodos esperados:</strong> P conforme o extrator selecionado; Ca, Mg e Al por
                  KCl; K e Na conforme o método do laboratório; H+Al por SMP ou
                  acetato; micronutrientes conforme indicação do laboratório.
                  Selecione a unidade original de cada resultado. Os cálculos usam mmolc/dm³, g/dm³ e g/kg. Alterar a unidade não altera o número digitado; confira ambos conforme o laudo.
                </div>
              </form>
            </TabsContent>

            <TabsContent value="relatorio" className="report-page ds-panel">
              {selected && reportLoading && (
                <p className="empty-copy">Gerando interpretação...</p>
              )}

              {selected && !reportLoading && !report && selected.analysis && (
                <p className="empty-copy">
                  Não foi possível carregar a interpretação desta amostragem.
                </p>
              )}

              {selected && report && (
                <>
                  <div className="report-head">
                    <div>
                      <p>RELATÓRIO DE INTERPRETAÇÃO</p>
                      <h2>
                        <span className="report-code">{selected.code}</span>
                        {selected.serviceOrderNumber && (
                          <span className="report-os"> · OS-{selected.serviceOrderNumber}</span>
                        )}
                      </h2>
                      <span className="report-meta">
                        {selected.crop} · {selected.block}/{selected.plot}/{selected.row} ·{" "}
                        {selected.depth}
                      </span>
                    </div>
                    <Button
                      className="no-print"
                      variant="outline"
                      onClick={() => window.print()}
                    >
                      <Printer /> Imprimir
                    </Button>
                    <div className="print-brand" aria-hidden="true">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img src="/solumnutri-symbol.png" alt="" width={38} height={35} />
                      <div>
                        <strong>SolumNutri</strong>
                        <span>Inteligência para análise de solo</span>
                      </div>
                    </div>
                  </div>

                  <section className="report-observations">
                    <h3>Observações</h3>
                    <p className={`sample-observations-text${selected.observations?.trim() ? "" : " is-empty"}`}>
                      {selected.observations?.trim() || "Nenhuma observação registrada na amostragem."}
                    </p>
                  </section>

                  <div className="summary-strip">
                    {report.summary.map((item) => (
                      <div key={item.label}>
                        <span>{item.label}</span>
                        <strong>{item.value}</strong>
                        <StatusPill level={item.level} />
                      </div>
                    ))}
                  </div>

                  <div className="report-grid">
                    <section>
                      <h3>Interpretação dos indicadores</h3>
                      <p>Classificação conforme os parâmetros atuais cadastrados para a cultura da amostra.
                        <a className="no-print" href="/parametros-interpretacao"> Configurar parâmetros</a>
                      </p>
                      <table>
                        <thead>
                          <tr>
                            <th>Indicador</th>
                            <th>Resultado</th>
                            <th>Classe</th>
                          </tr>
                        </thead>
                        <tbody>
                          {report.indicators.map((item) => (
                            <tr key={item.label}>
                              <td>{item.label}</td>
                              <td>{item.formatted}</td>
                              <td>
                                <StatusPill level={item.level} />
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </section>

                    <section>
                      <h3>Equilíbrio e correlações</h3>
                      <div className="insight-list">
                        {report.insights.map((item) => (
                          <article key={item.title} className={item.tone}>
                            <strong>{item.title}</strong>
                            <p>{item.text}</p>
                          </article>
                        ))}
                      </div>
                    </section>
                  </div>

                  <div className="derived">
                    <h3>Cálculos derivados</h3>
                    {report.derived.map((item) => (
                      <div key={item.label}>
                        <span>{item.label}</span>
                        <strong>{item.value}</strong>
                        <small>{item.formula}</small>
                      </div>
                    ))}
                  </div>

                  <footer className="report-footer">
                    <strong>Nota técnica</strong>
                    <p>
                      Triagem baseada em métodos de fertilidade de solos tropicais e
                      referências do Ceará. Faixas gerais não substituem recomendação de
                      calagem ou adubação por cultura, produtividade esperada, histórico e
                      validação de engenheiro agrônomo.
                    </p>
                  </footer>
                </>
              )}
            </TabsContent>
          </Tabs>
        </section>
      </div>
      <Dialog open={confirmation !== null} onOpenChange={open => { if (!open) { setConfirmation(null); setPendingAnalysis(null); } }}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{confirmation === "first" ? "Confirmar primeiro laudo" : "Reabrir registro atualizado?"}</DialogTitle>
            <DialogDescription>{confirmation === "first"
              ? "Confira os resultados e as unidades. Após este salvamento, inclusive com campos em branco, somente um administrador poderá corrigir ou complementar o laudo."
              : "As alterações não salvas desta tela serão descartadas. Os dados mais recentes serão carregados para sua conferência; nenhum rascunho será aplicado automaticamente."}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => { setConfirmation(null); setPendingAnalysis(null); }}>Cancelar</Button>
            <Button type="button" onClick={() => {
              if (confirmation === "first" && pendingAnalysis) void performAnalysisSave(pendingAnalysis);
              else if (selected) { setConfirmation(null); void chooseSample(selected); }
            }}>{confirmation === "first" ? "Confirmar registro do laudo" : "Descartar rascunho e reabrir"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  );
}
