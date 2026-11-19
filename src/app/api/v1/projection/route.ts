import { getInterestProjection } from "@/server/core/projection"
import { authed } from "@/server/session"
import { z } from "zod"

const querySchema = z.object({
  cursor: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}_[0-9a-f-]{36}$/i, "Invalid cursor")
    .optional(),
  limit: z.coerce.number().int().positive().max(100).optional(),
  sortDirection: z.enum(["asc", "desc"]).optional(),
  statusFilter: z.array(z.enum(["not_donated", "partially_donated", "fully_donated"])).optional(),
})

export const GET = authed(async (userId, req) => {
  const params = new URL(req.url).searchParams
  const parsed = querySchema.safeParse({
    cursor: params.get("cursor") ?? undefined,
    limit: params.get("limit") ?? undefined,
    sortDirection: params.get("sortDirection") ?? undefined,
    statusFilter: params.has("statusFilter") ? params.getAll("statusFilter") : undefined,
  })

  if (!parsed.success) {
    return Response.json({ success: false, message: "Invalid query" }, { status: 400 })
  }

  return Response.json(await getInterestProjection(userId, parsed.data))
})
