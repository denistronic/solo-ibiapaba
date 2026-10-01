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
  { key: "phCaCl2", label: "pH em CaCl₂", unit: "1:2,5" },

  { key: "organicMatter", label: "Matéria orgânica", unit: "g/dm³" },
  { key: "organicCarbon", label: "Carbono orgânico total", unit: "g/dm³" },
  { key: "phosphorus", label: "Fósforo (P) — Resina", unit: "g/dm³" },

  { key: "calcium", label: "Cálcio (Ca)", unit: "mmolc/dm³" },
  { key: "magnesium", label: "Magnésio (Mg)", unit: "mmolc/dm³" },
  { key: "potassium", label: "Potássio (K)", unit: "mmolc/dm³" },
  { key: "sodium", label: "Sódio (Na)", unit: "mmolc/dm³" },

  { key: "aluminum", label: "Alumínio total (Al)", unit: "mmolc/dm³" },
  { key: "hAl", label: "Acidez total (H+Al)", unit: "mmolc/dm³" },
  { key: "cecReported", label: "CTC informada", unit: "mmolc/dm³" },
  { key: "baseSatReported", label: "V informado", unit: "%" },

  { key: "sulfur", label: "Enxofre (S)", unit: "g/dm³" },
  { key: "boron", label: "Boro (B)", unit: "g/dm³" },
  { key: "copper", label: "Cobre (Cu)", unit: "g/dm³" },
  { key: "iron", label: "Ferro (Fe)", unit: "g/dm³" },
  { key: "manganese", label: "Manganês (Mn)", unit: "g/dm³" },
  { key: "zinc", label: "Zinco (Zn)", unit: "g/dm³" },

  { key: "clay", label: "Argila", unit: "g/kg" },
  { key: "silt", label: "Silte", unit: "g/kg" },
  { key: "sand", label: "Areia total", unit: "g/kg" },
] as const;
