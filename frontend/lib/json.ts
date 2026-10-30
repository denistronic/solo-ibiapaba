import { parse } from "lossless-json";

/** Números da API ficam como texto decimal, sem perda no JSON.parse do navegador. */
export function parseApiJson<T>(text: string): T {
  return parse(text, null, value => value) as T;
}
