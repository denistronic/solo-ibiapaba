"use client";

import { useEffect, useMemo, useState } from "react";
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
  sampledAt: new Date().toISOString().slice(0, 10),
  temperature: "",
  observations: "",
  depth: "0–20 cm",
});

const makeEmptyAnalysis = () =>
  Object.fromEntries(ANALYSIS_FIELDS.map((field) => [field.key, ""])) as SoilAnalysis;

function messageFromError(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function StatusPill({ level }: { level: string }) {
  const slug = level
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replaceAll(" ", "-");

  return <span className={`status status-${slug}`}>{level}</span>;
}

export default function Home() {
  // NOVO (Fase 4): pega o usuário logado e a função de logout do contexto
  const { user, loading: authLoading, logout } = useAuth();
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

  // NOVO (Fase 4): se não estiver logado (e já terminou de checar), manda pro /login
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/login");
    }
  }, [authLoading, user, router]);

  // Seleciona automaticamente a amostra passada via parâmetro de URL (ex: vindas do histórico)
  useEffect(() => {
    if (sampleIdParam && samples.length > 0) {
      const targetSample = samples.find((s) => s.id === sampleIdParam);
      if (targetSample) {
        void chooseSample(targetSample);
      }
    }
  }, [sampleIdParam, samples]);

  // NOVO (Fase 5): função chamada pelo botão "Sair"
  function handleLogout() {
    logout();
    router.push("/login");
  }

  const selected = samples.find((sample) => sample.id === selectedId) ?? null;
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
  }, []);

  useEffect(() => {
    async function bootstrap() {
      try {
        await getHealth();
        const data = await listSamples();
        setSamples(data);
        setOnline(true);
      } catch {
        setOnline(false);
      } finally {
        setLoadingSamples(false);
      }
    }

    void bootstrap();
  }, []);

  async function loadReport(sampleId: string) {
    setReportLoading(true);
    try {
      const data = await getInterpretation(sampleId);
      setReport(data);
      return data;
    } catch (error) {
      setReport(null);
      toast.error(messageFromError(error, "Não foi possível carregar a interpretação."));
      return null;
    } finally {
      setReportLoading(false);
    }
  }

  async function createSample(event: React.FormEvent) {
    event.preventDefault();

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
      const sample = await createSampleApi({
        ...meta,
        temperature: Number(meta.temperature),
      });
      setSamples((current) => [sample, ...current]);
      setSelectedId(sample.id);
      setAnalysis(makeEmptyAnalysis());
      setReport(null);
      setMeta(makeEmptyMeta());
      setOnline(true);
      setTab("analise");
      toast.success(`Amostragem ${sample.code} cadastrada`);
    } catch (error) {
      setOnline(false);
      toast.error(
        messageFromError(
          error,
          "Não foi possível cadastrar. Verifique o backend Spring Boot.",
        ),
      );
    }
  }

  async function saveAnalysis(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedId) return;

    const units = analysis.units ?? {};
    const needsDensity = ANALYSIS_FIELDS.some((field) =>
      field.unit === "g/dm³" && units[field.key]?.endsWith("/kg") &&
      analysis[field.key] !== "" && analysis[field.key] != null,
    );
    if (needsDensity && !(Number(analysis.density) > 0)) {
      toast.error("Informe a densidade de conversão para resultados por massa (g/kg ou mg/kg).");
      return;
    }
    const numeric = {
      ...Object.fromEntries(ANALYSIS_FIELDS.map(({ key }) => [
        key, analysis[key] === "" || analysis[key] == null ? null : Number(analysis[key]),
      ])),
      units,
      density: analysis.density === "" || analysis.density == null ? null : Number(analysis.density),
      phosphorusMethod: analysis.phosphorusMethod ?? "Resina",
    } as SoilAnalysis;

    try {
      const updated = await updateAnalysis(selectedId, numeric);
      setSamples((current) =>
        current.map((sample) => (sample.id === selectedId ? updated : sample)),
      );
      setAnalysis({ ...makeEmptyAnalysis(), ...(updated.analysis ?? {}) });
      setOnline(true);

      const interpreted = await loadReport(selectedId);
      if (interpreted) {
        setTab("relatorio");
        toast.success("Laudo salvo e interpretado pelo backend Java");
      }
    } catch (error) {
      toast.error(messageFromError(error, "Não foi possível salvar o laudo."));
    }
  }

  async function chooseSample(sample: Sample) {
    setSelectedId(sample.id);
    setAnalysis({ ...makeEmptyAnalysis(), ...(sample.analysis ?? {}) });
    setReport(null);

    if (sample.analysis) {
      setTab("relatorio");
      await loadReport(sample.id);
    } else {
      setTab("analise");
    }
  }

  function startNewSample() {
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
                  <i className={sample.analysis ? "done" : "pending"}>
                    {sample.analysis ? "Laudo analisado" : "Aguardando laudo"}
                  </i>
                </button>
              ))}

            {!loadingSamples && !filtered.length && (
              <p className="empty-copy">Nenhuma amostragem cadastrada.</p>
            )}
          </div>

          <p className={`connection ${online ? "online" : "offline"}`}>
            {online ? "Backend Java conectado" : "Backend Java desconectado"}
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

          <Tabs value={tab} onValueChange={setTab} className="workflow">
            <TabsList className="steps no-print">
              <TabsTrigger value="cadastro">
                <b>01</b> Amostragem
              </TabsTrigger>
              <TabsTrigger value="analise" disabled={!selected}>
                <b>02</b> Laudo
              </TabsTrigger>
              <TabsTrigger value="relatorio" disabled={!selected?.analysis}>
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

              <form onSubmit={createSample} className="form-grid">
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
                        step={key === "temperature" ? "any" : undefined}
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
                  <Button type="submit">Gerar amostra e salvar</Button>
                </div>
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

              <form onSubmit={saveAnalysis}>
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
                      <NativeSelect id="phosphorusMethod" value={analysis.phosphorusMethod ?? "Resina"}
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
                      <Input id="density" type="number" min="0" step="any"
                        value={analysis.density ?? ""}
                        onChange={(event) => setAnalysis({ ...analysis, density: event.target.value })}
                        aria-describedby="density-help" />
                    </div>
                  </div>

                  <div className="form-actions analysis-actions">
                    <Button className="analysis-back-button" variant="outline" type="button" onClick={() => setTab("cadastro")}>
                      Voltar
                    </Button>
                    <Button type="submit">Salvar e interpretar</Button>
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
                <p className="empty-copy">Gerando interpretação no backend Java...</p>
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
    </main>
  );
}
