import { reconcileUserInterests } from "@/server/core/reconcile"
import { syncTransactions } from "@/server/core/transactions"
import { authed } from "@/server/session"
import { after } from "next/server"

export const POST = authed(async (userId, req) => {
  const body = await req.json().catch(() => null)
  const result = await syncTransactions(userId, body)

  if (result.success) after(() => reconcileUserInterests(userId))
  return Response.json(result, { status: result.success ? 200 : 400 })
})
