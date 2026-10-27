"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import AppHeader from "@/components/AppHeader";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/context/AuthContext";
import { request } from "@/lib/api";
import styles from "./parametros.module.css";

type Parameter = { indicator: string; unit: string; minimum: number | null; maximum: number | null };
type Profile = { crop: string; parameters: Parameter[] };
type Row = { indicator: string; unit: string; minimum: string; maximum: string };
const rowsFrom = (profile: Profile): Row[] => profile.parameters.map(p => ({ ...p,
  minimum: p.minimum === null ? "" : String(p.minimum), maximum: p.maximum === null ? "" : String(p.maximum),
}));

export default function InterpretationParametersPage() {
  const { user, loading } = useAuth();
  const router = useRouter();
  const canEdit = user?.role === "ADMIN";
  const [crop, setCrop] = useState("Acerola");
  const [loadedCrop, setLoadedCrop] = useState("");
  const [rows, setRows] = useState<Row[]>([]);
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
    if (!user) return;
    let cancelled = false;
    request<Profile>("/interpretation-parameters?crop=Acerola").then(profile => {
      if (!cancelled) { setRows(rowsFrom(profile)); setLoadedCrop(profile.crop); }
    }).catch(e => { if (!cancelled) setError(e instanceof Error ? e.message : "Não foi possível carregar os parâmetros."); });
    return () => { cancelled = true; };
  }, [user, loading, router]);

  async function loadCulture() {
    if (!crop.trim()) { setError("Informe a cultura."); return; }
    if (dirty && !window.confirm("Há alterações não salvas. Deseja descartá-las e carregar a cultura?")) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const profile = await request<Profile>(`/interpretation-parameters?crop=${encodeURIComponent(crop.trim())}`);
      setRows(rowsFrom(profile)); setLoadedCrop(profile.crop); setCrop(profile.crop); setDirty(false);
    } catch (e) { setError(e instanceof Error ? e.message : "Não foi possível carregar a cultura."); }
    finally { setBusy(false); }
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(""); setMessage("");
    if (!canEdit) { setError("Somente o administrador pode alterar os parâmetros."); return; }
    for (const row of rows) {
      if (row.minimum === "" && row.maximum === "") continue;
      if (row.minimum === "" || row.maximum === "" || !Number.isFinite(Number(row.minimum)) || !Number.isFinite(Number(row.maximum)) || Number(row.minimum) < 0 || Number(row.minimum) > Number(row.maximum)) {
        setError(`Revise ${row.indicator}: informe os dois limites, com mínimo menor ou igual ao máximo.`); return;
      }
    }
    setBusy(true);
    try {
      const profile = await request<Profile>("/interpretation-parameters", { method: "PUT", body: JSON.stringify({ crop: loadedCrop,
        parameters: rows.map(row => ({ indicator: row.indicator, minimum: row.minimum === "" ? null : Number(row.minimum), maximum: row.maximum === "" ? null : Number(row.maximum) })),
      }) });
      setRows(rowsFrom(profile)); setDirty(false);
      setMessage(`Parâmetros de ${profile.crop} salvos. Todas as análises de solo dessa cultura usarão estes limites ao consultar a interpretação.`);
    } catch (e) { setError(e instanceof Error ? e.message : "Não foi possível salvar os parâmetros."); }
    finally { setBusy(false); }
  }

  function edit(index: number, field: "minimum" | "maximum", value: string) {
    if (!canEdit) return;
    setRows(current => current.map((row, i) => i === index ? { ...row, [field]: value } : row));
    setDirty(true); setMessage("");
  }
  if (loading || !user) return <main className={styles.page}><p>Carregando...</p></main>;
  return <main className={styles.page}>
    <AppHeader />
    <div className={styles.content}>
      <div className={styles.heading}><h1>Parâmetros de interpretação</h1><Button variant="outline" onClick={() => {
        if (!dirty || window.confirm("Sair sem salvar os parâmetros alterados?")) router.push("/");
      }}>Voltar ao início</Button></div>
      <p>{canEdit ? "Cadastre" : "Consulte"} os limites dos indicadores para cada cultura. Os parâmetros ficam salvos no sistema e são compartilhados por todas as análises de solo da mesma cultura.</p>
      <section className={styles.card} aria-label="Selecionar cultura">
        <label htmlFor="culture">Cultura</label>
        <div className={styles.culture}><input id="culture" list="cultures" maxLength={120} value={crop} disabled={busy} onChange={e => setCrop(e.target.value)} />
          <datalist id="cultures"><option value="Acerola" /></datalist>
          <Button disabled={busy} onClick={loadCulture}>Carregar cultura</Button></div>
        <p>Para outra cultura, informe o nome e clique em Carregar cultura. Os parâmetros da Acerola não serão copiados.</p>
      </section>
      {!canEdit && <p role="status">Modo de consulta. Somente o administrador pode alterar os parâmetros de interpretação.</p>}
      <p className={styles.rules}><strong>Baixo:</strong> abaixo do mínimo. <strong>Médio:</strong> do mínimo ao máximo, incluindo os limites. <strong>Alto:</strong> acima do máximo. Deixe os dois campos vazios para não classificar o indicador.</p>
      <p>Equilíbrio e Correlações e Cálculos Derivados mantêm as regras e fórmulas atuais e não recebem estes parâmetros.</p>
      {error && <p role="alert" className={styles.error}>{error}</p>}
      {message && <p role="status" className={styles.success}>{message}</p>}
      <form onSubmit={save} className={styles.card}>
        <h2>Indicadores — {loadedCrop || "carregando"}</h2>
        <div className={styles.tableWrap}><table><thead><tr><th>Indicador</th><th>Unidade</th><th>Mínimo (Médio)</th><th>Máximo (Médio)</th></tr></thead>
          <tbody>{rows.map((row, index) => <tr key={row.indicator}><th scope="row">{row.indicator}</th><td>{row.unit || "—"}</td>
            <td><input aria-label={`Mínimo de ${row.indicator}`} type="number" step="any" min="0" value={row.minimum} disabled={!canEdit || busy || crop.trim() !== loadedCrop} onChange={e => edit(index, "minimum", e.target.value)} /></td>
            <td><input aria-label={`Máximo de ${row.indicator}`} type="number" step="any" min="0" value={row.maximum} disabled={!canEdit || busy || crop.trim() !== loadedCrop} onChange={e => edit(index, "maximum", e.target.value)} /></td>
          </tr>)}</tbody></table></div>
        <div className={styles.actions}><span>{rows.filter(r => r.minimum !== "" && r.maximum !== "").length} indicadores configurados{dirty ? " · Alterações não salvas" : ""}</span>
          {canEdit && <Button type="submit" disabled={busy || !loadedCrop || crop.trim() !== loadedCrop || !dirty}>{busy ? "Aguarde..." : "Salvar parâmetros"}</Button>}</div>
        {loadedCrop && crop.trim() !== loadedCrop && <p>Carregue a cultura informada antes de editar e salvar seus parâmetros.</p>}
      </form>
    </div>
  </main>;
}
