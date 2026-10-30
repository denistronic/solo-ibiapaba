/** Normaliza decimal sem passar por Number, preservando NUMERIC(24,10). */
export function decimal(value: string | number, label: string, fraction = 10, integer = 14): string {
  const text = String(value).trim();
  const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(text);
  if (!match) throw new Error(`Informe um número válido para ${label}.`);
  const exponent = Number(match[5] ?? 0);
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 1000) throw new Error(`Valor fora do intervalo permitido para ${label}.`);
  let digits = ((match[2] ?? "0") + (match[3] ?? match[4] ?? "")).replace(/^0+/, "");
  if (!digits) return "0";
  let scale = (match[3] ?? match[4] ?? "").length - exponent;
  while (digits.endsWith("0")) { digits = digits.slice(0, -1); scale--; }
  if (Math.max(0, scale) > fraction || Math.max(0, digits.length - scale) > integer) {
    throw new Error(`${label}: use no máximo ${integer} dígitos inteiros e ${fraction} casas decimais.`);
  }
  const sign = match[1] === "-" ? "-" : "";
  if (scale <= 0) return sign + digits + "0".repeat(-scale);
  const padded = digits.padStart(scale + 1, "0");
  return sign + padded.slice(0, -scale) + "." + padded.slice(-scale);
}

export function compareDecimal(left: string, right: string): number {
  const scaled = (value: string) => {
    const [whole, fractional = ""] = value.split(".");
    const negative = whole.startsWith("-");
    const digits = whole.replace("-", "") + fractional.padEnd(10, "0");
    return BigInt(negative ? "-" + digits : digits);
  };
  const a = scaled(left), b = scaled(right);
  return a < b ? -1 : a > b ? 1 : 0;
}

export function boundedDecimal(value: string | number, label: string, options: { min?: string; max?: string; fraction?: number; integer?: number } = {}): string {
  const normalized = decimal(value, label, options.fraction, options.integer);
  if (compareDecimal(normalized, options.min ?? "0") < 0 || (options.max && compareDecimal(normalized, options.max) > 0)) {
    throw new Error(`${label}: valor fora do intervalo permitido${options.max ? ` (de ${options.min ?? "0"} a ${options.max})` : ""}.`);
  }
  return normalized;
}

export function passwordError(password: string, creating = true): string | null {
  if (creating && password.length < 8) return "A senha deve ter pelo menos 8 caracteres.";
  if (new TextEncoder().encode(password).length > 72) return "A senha deve ter no máximo 72 bytes em UTF-8; letras com acento podem ocupar mais de um byte.";
  return null;
}

export function rangeMaximum(indicator: string, unit: string): string | undefined {
  return unit === "%" ? "100" : unit === "g/kg" ? "1000" : indicator === "pH em água (calculado)" ? "14" : undefined;
}

export function businessDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en", { timeZone: "America/Fortaleza", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  return ["year", "month", "day"].map(type => parts.find(part => part.type === type)!.value).join("-");
}
