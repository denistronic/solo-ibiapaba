import type { Sample, SoilAnalysis, SoilReport } from "@/lib/soil";

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "") ??
  "http://localhost:8080/api";

type ApiErrorPayload = {
  message?: string;
  error?: string;
  fields?: Record<string, string>;
};

export class ApiError extends Error {
  status: number;
  fields: Record<string, string>;

  constructor(message: string, status: number, fields: Record<string, string> = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fields = fields;
  }
}

async function parseError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload = {};

  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Mantém mensagem HTTP genérica quando a resposta não é JSON.
  }

  const fieldMessage = payload.fields
    ? Object.values(payload.fields).filter(Boolean).join(" · ")
    : "";
  const message =
    fieldMessage ||
    payload.message ||
    payload.error ||
    `Erro HTTP ${response.status}`;

  return new ApiError(message, response.status, payload.fields ?? {});
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    throw await parseError(response);
  }

  return (await response.json()) as T;
}

export function getHealth() {
  return request<{ status: string }>("/health");
}

export function listSamples() {
  return request<Sample[]>("/samples");
}

export function getSample(id: string) {
  return request<Sample>(`/samples/${id}`);
}

export function createSample(payload: {
  block: string;
  plot: string;
  row: string;
  crop: string;
  sampledAt: string;
  temperature: number;
  depth: string;
}) {
  return request<Sample>("/samples", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateAnalysis(id: string, analysis: SoilAnalysis) {
  return request<Sample>(`/samples/${id}/analysis`, {
    method: "PUT",
    body: JSON.stringify(analysis),
  });
}

export function getInterpretation(id: string) {
  return request<SoilReport>(`/samples/${id}/interpretation`);
}
