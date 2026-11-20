import { reconcileUserInterests } from "@/server/core/reconcile"
import { syncTransactions } from "@/server/core/transactions"
import { authed } from "@/server/session"

export const POST = authed(async (userId, req) => {
  const body = await req.json().catch(() => null)
  const result = await syncTransactions(userId, body)

  // Reconcile before responding so clients that refetch right away see updated statuses.
  if (result.success) await reconcileUserInterests(userId)
  return Response.json(result, { status: result.success ? 200 : 400 })
})
