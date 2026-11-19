import { auth } from "@/lib/auth"
import { headers } from "next/headers"

// Works for both the web cookie and an Android `Authorization: Bearer` token.
export async function getUserId() {
  const session = await auth.api.getSession({ headers: await headers() })
  return session?.user.id ?? null
}

// Wraps a route handler: 401 without a session, otherwise passes the session's userId.
export function authed(handler: (userId: string, req: Request) => Promise<Response>) {
  return async (req: Request) => {
    const userId = await getUserId()
    if (!userId) return Response.json({ success: false, message: "Unauthorized" }, { status: 401 })
    return handler(userId, req)
  }
}
