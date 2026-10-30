import type { Sample, SoilAnalysis, SoilReport } from "@/lib/soil";
import { parseApiJson } from "@/lib/json";
import { versionHeader } from "@/lib/sample-permissions";
import { clearToken, getToken } from "@/lib/token";

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

export async function parseError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload = {};

  try {
    payload = parseApiJson<ApiErrorPayload>(await response.text());
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

export const SESSION_EXPIRED = "solo-ibiapaba:session-expired";

export async function request<T>(path: string, init?: RequestInit, authenticated = true): Promise<T> {
  const token = authenticated ? getToken() : null;

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError("Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.", 0);
  }

  if (!response.ok) {
    if (response.status === 401 && token && token === getToken()) {
      clearToken();
      if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_EXPIRED));
    }
    throw await parseError(response);
  }

  if (response.status === 204) return undefined as T;
  return parseApiJson<T>(await response.text());
}

export function getHealth() {
  return request<{ status: string }>("/health", undefined, false);
}

export function listSamples() {
  return request<Sample[]>("/samples");
}

export function getSample(id: string) {
  return request<Sample>(`/samples/${id}`);
}

export type SamplePayload = {
  serviceOrderNumber: string;
  block: string;
  plot: string;
  row: string;
  crop: string;
  sampledAt: string;
  temperature: string | number;
  observations?: string;
  depth: string;
};

export function createSample(payload: SamplePayload) {
  return request<Sample>("/samples", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateAnalysis(id: string, analysis: SoilAnalysis, version: Sample["recordVersion"]) {
  return request<Sample>(`/samples/${id}/analysis`, {
    method: "PUT",
    headers: { "If-Match": versionHeader(version) },
    body: JSON.stringify(analysis),
  });
}

export function getInterpretation(id: string) {
  return request<SoilReport>(`/samples/${id}/interpretation`);
}


export function updateSample(id: string, payload: SamplePayload, version: Sample["recordVersion"]) {
  return request<Sample>(`/samples/${id}`, { method: "PUT", headers: { "If-Match": versionHeader(version) }, body: JSON.stringify(payload) });
}
