import { getLastTransactionDateByBank } from "@/server/core/stats"
import { authed } from "@/server/session"

export const GET = authed(async userId => Response.json(await getLastTransactionDateByBank(userId)))
