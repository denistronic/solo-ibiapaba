"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ClipboardPlus,
  FlaskConical,
  Leaf,
  Printer,
  Search,
  Sprout,
} from "lucide-react";
import { toast, Toaster } from "sonner";
import { useRouter } from "next/navigation"; // NOVO (Fase 4)
import { useAuth } from "@/context/AuthContext"; // NOVO (Fase 4)
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  updateAnalysis,
} from "@/lib/api";
import {
  ANALYSIS_FIELDS,
  type Sample,
  type SoilAnalysis,
  type SoilReport,
} from "@/lib/soil";

const makeEmptyMeta = () => ({
  block: "",
  plot: "",
  row: "",
  crop: "Acerola",
  sampledAt: new Date().toISOString().slice(0, 10),
  temperature: "",
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

  // NOVO (Fase 4): se não estiver logado (e já terminou de checar), manda pro /login
  useEffect(() => {
    if (!authLoading && !user) {
      router.push("/login");
    }
  }, [authLoading, user, router]);

  // NOVO (Fase 5): função chamada pelo botão "Sair"
  function handleLogout() {
    logout();
    router.push("/login");
  }

  const [samples, setSamples] = useState<Sample[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [meta, setMeta] = useState(makeEmptyMeta);
  const [analysis, setAnalysis] = useState<SoilAnalysis>(makeEmptyAnalysis);
  const [report, setReport] = useState<SoilReport | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState("cadastro");
  const [online, setOnline] = useState(false);
  const [loadingSamples, setLoadingSamples] = useState(true);

  const selected = samples.find((sample) => sample.id === selectedId) ?? null;
  const filtered = useMemo(
    () =>
      samples.filter((sample) =>
        `${sample.code} ${sample.block} ${sample.plot} ${sample.crop}`
          .toLowerCase()
          .includes(query.toLowerCase()),
      ),
    [samples, query],
  );

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

    const numeric = Object.fromEntries(
      Object.entries(analysis).map(([key, value]) => [
        key,
        value === "" || value === null ? null : Number(value),
      ]),
    ) as SoilAnalysis;

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
    <main className="app-shell">
      <Toaster position="top-right" richColors />

      <header className="topbar no-print">
        <div className="brand-mark">
          <Sprout size={22} />
        </div>
        <div>
          <strong>Solo Ibiapaba</strong>
          <span>Inteligência para análise de solo</span>
        </div>
        <div className="region-tag">
          <Leaf size={15} /> Serra da Ibiapaba · CE
        </div>

        {/* NOVO (Fase 5): usuário logado, atalho de admin e botão de sair */}
        <div
          style={{
            marginLeft: "auto",
            display: "flex",
            alignItems: "center",
            gap: "0.75rem",
          }}
        >
          {user.role === "ADMIN" && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => router.push("/admin/usuarios")}
            >
              Gerenciar usuários
            </Button>
          )}

          <span style={{ fontSize: "0.875rem", color: "#6b7280" }}>
            {user.name} · <strong>{user.role === "ADMIN" ? "Administrador" : "Usuário"}</strong>
          </span>

          <Button variant="outline" size="sm" onClick={handleLogout}>
            Sair
          </Button>
        </div>
      </header>

      <div className="workspace">
        <aside className="sidebar-panel no-print">
          <Button className="new-button" onClick={startNewSample}>
            <ClipboardPlus /> Nova amostragem
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
                  <span className="sample-code">{sample.code}</span>
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

        <section className="content-panel">
          <div className="page-intro no-print">
            <p>GESTÃO DE FERTILIDADE</p>
            <h1>{selected ? selected.code : "Nova amostragem"}</h1>
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

            <TabsContent value="cadastro" className="paper">
              <div className="section-title">
                <ClipboardPlus />
                <div>
                  <h2>Dados da coleta</h2>
                  <p>Identificação e condições da amostragem em campo.</p>
                </div>
              </div>

              <form onSubmit={createSample} className="form-grid">
                {[
                  ["Bloco", "block", "Ex.: C"],
                  ["Quadra", "plot", "Ex.: Q12"],
                  ["Linha", "row", "Ex.: L08"],
                  ["Cultura", "crop", "Ex.: Acerola"],
                  ["Data da coleta", "sampledAt", ""],
                  ["Temperatura (°C)", "temperature", "Ex.: 27"],
                ].map(([label, key, placeholder]) => (
                  <div className="field" key={key}>
                    <Label htmlFor={key}>{label}</Label>
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
                  </div>
                ))}

                <div className="field">
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

                <div className="form-actions">
                  <Button type="submit">Gerar número e salvar</Button>
                </div>
              </form>
            </TabsContent>

            <TabsContent value="analise" className="paper">
              <div className="section-title">
                <FlaskConical />
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
                        <span>{field.unit}</span>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="method-note">
                  <strong>Métodos esperados:</strong> P, K e Na por Mehlich-1; Ca, Mg e
                  Al por KCl; H+Al por SMP ou acetato; micronutrientes conforme indicação
                  do laboratório.
                </div>

                <div className="form-actions">
                  <Button variant="outline" type="button" onClick={() => setTab("cadastro")}>
                    Voltar
                  </Button>
                  <Button type="submit">Salvar e interpretar</Button>
                </div>
              </form>
            </TabsContent>

            <TabsContent value="relatorio" className="report-page">
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
                      <h2>{selected.code}</h2>
                      <span>
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
                  </div>

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
