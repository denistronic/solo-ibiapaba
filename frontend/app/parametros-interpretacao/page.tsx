"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Settings } from "lucide-react";
import AppHeader from "@/components/AppHeader";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/context/AuthContext";
import { request } from "@/lib/api";
import styles from "./parametros.module.css";

type Parameter = { indicator: string; unit: string; minimum: number | null; maximum: number | null };
type Profile = { crop: string; parameters: Parameter[] };
type Row = { indicator: string; unit: string; minimum: string; maximum: string };
type ScreenMode = "empty" | "view" | "edit" | "create";

const rowsFrom = (profile: Profile): Row[] => profile.parameters.map((parameter) => ({
  indicator: parameter.indicator,
  unit: parameter.unit,
  minimum: parameter.minimum === null ? "" : String(parameter.minimum),
  maximum: parameter.maximum === null ? "" : String(parameter.maximum),
}));

const cultureKey = (value: string) => value.trim().normalize("NFD")
  .replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("pt-BR");

export default function InterpretationParametersPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const canEdit = user?.role === "ADMIN";

  const [cultures, setCultures] = useState<string[]>([]);
  const [selectedCulture, setSelectedCulture] = useState("");
  const [loadedCrop, setLoadedCrop] = useState("");
  const [draftCrop, setDraftCrop] = useState("");
  const [rows, setRows] = useState<Row[]>([]);
  const [mode, setMode] = useState<ScreenMode>("empty");
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (loading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    let cancelled = false;
    request<string[]>("/interpretation-parameters/cultures")
      .then((items) => { if (!cancelled) setCultures(items); })
      .catch((reason) => {
        if (!cancelled) setError(reason instanceof Error ? reason.message : "Não foi possível listar as culturas.");
      });
    return () => { cancelled = true; };
  }, [user, loading, router]);

  function confirmDiscardChanges() {
    return !dirty || window.confirm("Há alterações não salvas. Deseja descartá-las?");
  }

  function changeCulture(value: string) {
    if (!confirmDiscardChanges()) return;
    setSelectedCulture(value);
    setLoadedCrop("");
    setDraftCrop("");
    setRows([]);
    setMode("empty");
    setDirty(false);
    setMessage("");
    setError("");
  }

  async function loadCulture() {
    if (!selectedCulture) {
      setError("Selecione uma cultura na lista.");
      return;
    }
    if (!confirmDiscardChanges()) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const profile = await request<Profile>(`/interpretation-parameters?crop=${encodeURIComponent(selectedCulture)}`);
      setRows(rowsFrom(profile));
      setLoadedCrop(profile.crop);
      setSelectedCulture(profile.crop);
      setDraftCrop("");
      setMode("view");
      setDirty(false);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível carregar a cultura.");
    } finally {
      setBusy(false);
    }
  }

  async function startAddingCulture() {
    if (!canEdit || !confirmDiscardChanges()) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const template = await request<Profile>("/interpretation-parameters/template");
      setSelectedCulture("");
      setLoadedCrop("");
      setDraftCrop("");
      setRows(rowsFrom(template));
      setMode("create");
      setDirty(false);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível abrir o cadastro da cultura.");
    } finally {
      setBusy(false);
    }
  }

  function startEditing() {
    if (!canEdit || mode !== "view") return;
    setMode("edit");
    setDirty(false);
    setMessage("");
    setError("");
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (!canEdit || (mode !== "edit" && mode !== "create")) return;

    const cropToSave = mode === "create" ? draftCrop.trim() : loadedCrop;
    if (!cropToSave) {
      setError("Informe o nome da cultura.");
      return;
    }
    if (cropToSave.length > 120) {
      setError("O nome da cultura deve ter no máximo 120 caracteres.");
      return;
    }
    if (mode === "create" && cultures.some((culture) => cultureKey(culture) === cultureKey(cropToSave))) {
      setError("Essa cultura já existe. Selecione-a na lista para carregar seus indicadores.");
      return;
    }
    for (const row of rows) {
      if (row.minimum === "" && row.maximum === "") continue;
      if (row.minimum === "" || row.maximum === "" ||
        !Number.isFinite(Number(row.minimum)) || !Number.isFinite(Number(row.maximum)) ||
        Number(row.minimum) < 0 || Number(row.minimum) > Number(row.maximum)) {
        setError(`Revise ${row.indicator}: informe os dois limites, com mínimo menor ou igual ao máximo.`);
        return;
      }
    }

    setBusy(true);
    try {
      const profile = await request<Profile>("/interpretation-parameters", {
        method: mode === "create" ? "POST" : "PUT",
        body: JSON.stringify({
          crop: cropToSave,
          parameters: rows.map((row) => ({
            indicator: row.indicator,
            minimum: row.minimum === "" ? null : Number(row.minimum),
            maximum: row.maximum === "" ? null : Number(row.maximum),
          })),
        }),
      });
      setRows(rowsFrom(profile));
      setLoadedCrop(profile.crop);
      setSelectedCulture(profile.crop);
      setDraftCrop("");
      setMode("view");
      setDirty(false);
      setMessage(`Parâmetros de ${profile.crop} salvos com sucesso.`);
      try {
        setCultures(await request<string[]>("/interpretation-parameters/cultures"));
      } catch {
        // O salvamento já foi concluído; a atualização da lista não invalida os dados salvos.
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Não foi possível salvar a cultura.");
    } finally {
      setBusy(false);
    }
  }

  function editRow(index: number, field: "minimum" | "maximum", value: string) {
    if (!canEdit || (mode !== "edit" && mode !== "create")) return;
    setRows((current) => current.map((row, rowIndex) =>
      rowIndex === index ? { ...row, [field]: value } : row,
    ));
    setDirty(true);
    setMessage("");
  }

  function goBack() {
    if (!confirmDiscardChanges()) return;
    router.push("/");
  }

  if (loading || !user) {
    return <main className={`app-shell home-app-shell ${styles.page}`}><p className={styles.loading}>Carregando...</p></main>;
  }

  const editing = mode === "edit" || mode === "create";
  const configuredCount = rows.filter((row) => row.minimum !== "" && row.maximum !== "").length;

  return (
    <main className={`app-shell home-app-shell ${styles.page}`}>
      <AppHeader />
      <div className={styles.content}>
        <div className={styles.pageHeading}>
          <div className={styles.pageHeadingCopy}>
            <p className={styles.eyebrow}>GESTÃO DE FERTILIDADE</p>
            <h1>Parâmetros de interpretação</h1>
            <p className={styles.pageDescription}>Cadastre e edite os limites dos indicadores para cada cultura.</p>
          </div>
          <button type="button" className={styles.backButton} onClick={goBack}>
            Voltar
          </button>
        </div>

        <section className={`${styles.card} ${styles.cultureCard}`} aria-labelledby="culture-heading">
          <div className={styles.sectionTitle}>
            <span className={styles.sectionIcon} aria-hidden="true"><Settings size={23} /></span>
            <h2 id="culture-heading">Configurar Cultura</h2>
          </div>
          <div className={styles.legend}>
            <p className={styles.legendHeading}>Interpretação dos valores:</p>
            <div className={styles.legendRow}><span className={`${styles.badge} ${styles.low}`}>BAIXO</span><span>Abaixo do valor <strong>MÍNIMO.</strong></span></div>
            <div className={styles.legendRow}><span className={`${styles.badge} ${styles.medium}`}>MÉDIO</span><span>A partir do valor <strong>MÍNIMO</strong> até o <strong>MÁXIMO.</strong></span></div>
            <div className={styles.legendRow}><span className={`${styles.badge} ${styles.high}`}>ALTO</span><span>Acima do valor <strong>MÁXIMO.</strong></span></div>
          </div>
          <div className={`${styles.cultureControls} ${!canEdit ? styles.viewerControls : ""}`}>
            <label className={styles.srOnly} htmlFor="culture-select">Selecione uma cultura</label>
            <select id="culture-select" value={selectedCulture} disabled={busy || mode === "create"}
              onChange={(event) => changeCulture(event.target.value)}>
              <option value="">Selecione uma cultura</option>
              {cultures.map((culture) => <option key={culture} value={culture}>{culture}</option>)}
            </select>
            <Button className={styles.actionButton} disabled={busy || !selectedCulture || mode === "create"} onClick={loadCulture}>Carregar</Button>
            {canEdit && <Button className={styles.actionButton} disabled={busy} onClick={startAddingCulture}>Adicionar</Button>}
          </div>
        </section>

        {error && <p role="alert" className={styles.error}>{error}</p>}
        {message && <p role="status" className={styles.success}>{message}</p>}
        {!canEdit && <p className={styles.readOnlyNotice}>Modo de consulta. Somente o administrador pode alterar os parâmetros de interpretação.</p>}

        {mode !== "empty" && (
          <form className={styles.indicatorsCard} onSubmit={save}>
            <div className={styles.indicatorsHeader}>
              {mode === "create" ? (
                <div className={styles.titleWithInput}>
                  <h2>Indicadores —</h2>
                  <label className={styles.srOnly} htmlFor="new-culture-name">Nome da Cultura</label>
                  <input id="new-culture-name" className={styles.cultureNameInput} type="text"
                    placeholder="Nome da Cultura" maxLength={120} autoComplete="off" value={draftCrop}
                    disabled={busy} required
                    onChange={(event) => { setDraftCrop(event.target.value); setDirty(true); setMessage(""); }} />
                </div>
              ) : <h2>Indicadores — <span>{loadedCrop}</span></h2>}
              {mode === "view" && canEdit && <Button className={styles.editButton} variant="outline" type="button" onClick={startEditing}>Editar</Button>}
            </div>
            <div className={styles.tableWrap}>
              <table>
                <thead><tr><th scope="col">INDICADOR</th><th scope="col">UNIDADE</th><th scope="col">MÍNIMO</th><th scope="col">MÁXIMO</th></tr></thead>
                <tbody>
                  {rows.map((row, index) => (
                    <tr key={row.indicator}>
                      <th scope="row">{row.indicator}</th>
                      <td>{row.unit || "—"}</td>
                      <td>{editing ? <input aria-label={`Mínimo de ${row.indicator}`} className={styles.rangeInput}
                        type="number" step="any" min="0" value={row.minimum} disabled={busy}
                        onChange={(event) => editRow(index, "minimum", event.target.value)} />
                        : <span className={styles.rangeValue}>{row.minimum || "—"}</span>}</td>
                      <td>{editing ? <input aria-label={`Máximo de ${row.indicator}`} className={styles.rangeInput}
                        type="number" step="any" min="0" value={row.maximum} disabled={busy}
                        onChange={(event) => editRow(index, "maximum", event.target.value)} />
                        : <span className={styles.rangeValue}>{row.maximum || "—"}</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className={styles.indicatorsFooter}>
              <span>{configuredCount} {configuredCount === 1 ? "indicador configurado" : "indicadores configurados"}{dirty ? " · Alterações não salvas" : ""}</span>
              {canEdit && editing && <Button className={styles.saveButton} type="submit" disabled={busy || (mode === "edit" && !dirty)}>{busy ? "Salvando..." : "Salvar"}</Button>}
            </div>
          </form>
        )}
      </div>
    </main>
  );
}
