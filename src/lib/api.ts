// Browser-side fetch for our own /api/v1 routes. Throws with the server's message on non-2xx.
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  })
  const body = await res.json().catch(() => null)
  if (!res.ok) throw new Error(body?.message ?? `Request failed (${res.status})`)
  return body as T
}
