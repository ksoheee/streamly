import type { components } from "@/api/schema";

export type Broadcast = components["schemas"]["BroadcastResponse"];
export type PublishToken = components["schemas"]["PublishTokenResponse"];
export type Me = components["schemas"]["MeResponse"];
export type BroadcastRequest = components["schemas"]["BroadcastRequest"];
export type ChatMessage = components["schemas"]["ChatResponse"];

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

function csrfToken(): string | undefined {
  return document.cookie
    .split("; ")
    .find((c) => c.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
}

async function ensureCsrf() {
  if (!csrfToken()) await fetch("/api/auth/csrf", { credentials: "same-origin" });
}

export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") {
    await ensureCsrf();
    headers["X-XSRF-TOKEN"] = decodeURIComponent(csrfToken() ?? "");
  }
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const res = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    let detail = `요청 실패 (${res.status})`;
    try {
      const problem = await res.json();
      if (problem.detail) detail = problem.detail;
    } catch {}
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}
