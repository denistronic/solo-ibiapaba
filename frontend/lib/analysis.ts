import { ANALYSIS_FIELDS, analysisUnitOptions, type SoilAnalysis } from "./soil.ts";
import { boundedDecimal, compareDecimal, decimal } from "./validation.ts";

export function buildAnalysisPayload(analysis: SoilAnalysis): SoilAnalysis {
  const units: Record<string, string> = {};
  const result: SoilAnalysis = { units, phosphorusMethod: analysis.phosphorusMethod ?? "Não informado" };
  if (!["Resina", "Mehlich 1", "Não informado"].includes(result.phosphorusMethod!)) throw new Error("Selecione um extrator de fósforo válido.");
  let needsDensity = false;
  for (const field of ANALYSIS_FIELDS) {
    const options = analysisUnitOptions(field.unit);
    const unit = analysis.units?.[field.key] ?? field.unit;
    if (options.length) {
      if (!options.includes(unit)) throw new Error(`Unidade inválida para ${field.label}.`);
      units[field.key] = unit;
    }
    const value = analysis[field.key];
    if (value == null || value === "") { result[field.key] = null; continue; }
    if (typeof value !== "string" && typeof value !== "number") throw new Error(`Informe um valor numérico para ${field.label}.`);
    const maximum = field.key === "phCaCl2" ? "14" : field.key === "baseSatReported" ? "100" : field.unit === "g/kg" ? (unit === "%" ? "100" : "1000") : undefined;
    result[field.key] = boundedDecimal(value, field.label, { max: maximum, fraction: field.key === "phCaCl2" ? 4 : 10 });
    if (field.unit === "g/dm³" && unit.endsWith("/kg")) needsDensity = true;
  }
  result.density = analysis.density == null || analysis.density === "" ? null : boundedDecimal(analysis.density, "Densidade de conversão");
  if (result.density !== null && compareDecimal(String(result.density), "0") <= 0) throw new Error("A densidade de conversão deve ser maior que zero.");
  if (needsDensity && !result.density) throw new Error("Informe a densidade de conversão para os resultados em g/kg ou mg/kg.");
  return result;
}

/** Apresenta os valores recebidos sem zeros artificiais da escala SQL ou perda de precisão. */
export function analysisDraft(stored?: SoilAnalysis | null): SoilAnalysis {
  const result: SoilAnalysis = { units: { ...(stored?.units ?? {}) }, density: "", phosphorusMethod: stored?.phosphorusMethod ?? "Não informado" };
  for (const field of ANALYSIS_FIELDS) {
    const value = stored?.[field.key];
    result[field.key] = value == null || value === "" ? "" : decimal(value as string | number, field.label);
  }
  if (stored?.density != null && stored.density !== "") result.density = decimal(stored.density, "Densidade");
  return result;
}
