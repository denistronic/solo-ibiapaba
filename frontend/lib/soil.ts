export type SoilAnalysis = Record<string, number | string | null>;

export type Sample = {
  id: string;
  code: string;
  block: string;
  plot: string;
  row: string;
  crop: string;
  sampledAt: string;
  temperature: string | null;
  depth: string;
  createdAt: string;
  analysis?: SoilAnalysis | null;
  analysisUpdatedAt?: string | null;
};

export type SoilReport = {
  summary: Array<{ label: string; value: string; level: string }>;
  indicators: Array<{ label: string; formatted: string; level: string }>;
  insights: Array<{ title: string; text: string; tone: string }>;
  derived: Array<{ label: string; value: string; formula: string }>;
};

export const ANALYSIS_FIELDS = [
  { key: "phWater", label: "pH em água", unit: "1:2,5" },
  { key: "phCaCl2", label: "pH CaCl₂", unit: "1:2,5" },
  { key: "organicMatter", label: "Matéria orgânica", unit: "g/dm³" },
  { key: "phosphorus", label: "Fósforo (P)", unit: "mg/dm³" },
  { key: "potassium", label: "Potássio (K)", unit: "mg/dm³" },
  { key: "sodium", label: "Sódio (Na)", unit: "mg/dm³" },
  { key: "calcium", label: "Cálcio (Ca)", unit: "cmolc/dm³" },
  { key: "magnesium", label: "Magnésio (Mg)", unit: "cmolc/dm³" },
  { key: "aluminum", label: "Alumínio (Al)", unit: "cmolc/dm³" },
  { key: "hAl", label: "Acidez potencial (H+Al)", unit: "cmolc/dm³" },
  { key: "sulfur", label: "Enxofre (S)", unit: "mg/dm³" },
  { key: "boron", label: "Boro (B)", unit: "mg/dm³" },
  { key: "copper", label: "Cobre (Cu)", unit: "mg/dm³" },
  { key: "iron", label: "Ferro (Fe)", unit: "mg/dm³" },
  { key: "manganese", label: "Manganês (Mn)", unit: "mg/dm³" },
  { key: "zinc", label: "Zinco (Zn)", unit: "mg/dm³" },
  { key: "cecReported", label: "CTC informada", unit: "cmolc/dm³" },
  { key: "baseSatReported", label: "V informado", unit: "%" },
  { key: "clay", label: "Argila", unit: "%" },
  { key: "sand", label: "Areia", unit: "%" },
  { key: "silt", label: "Silte", unit: "%" },
  { key: "ec", label: "Condutividade elétrica", unit: "dS/m" },
] as const;
