import { addDonation } from "@/server/core/donations"
import { reconcileUserInterests } from "@/server/core/reconcile"
import { authed } from "@/server/session"

export const POST = authed(async (userId, req) => {
  const body = await req.json().catch(() => null)
  const result = await addDonation(userId, body?.amount)

  // Reconcile before responding so clients that refetch right away see updated statuses.
  if (result.success) await reconcileUserInterests(userId)
  return Response.json(result, { status: result.success ? 200 : 400 })
})
