import { db } from "@/db/drizzle"
import { interestProjection } from "@/db/schema"
import { and, asc, desc, eq, inArray, lt, or } from "drizzle-orm"

export type InterestProjection = typeof interestProjection.$inferSelect
export type StatusFilter = InterestProjection["status"][]
export type ProjectionPage = Awaited<ReturnType<typeof getInterestProjection>>

export async function getInterestProjection(
  userId: string,
  {
    cursor,
    limit = 50,
    sortDirection = "desc",
    statusFilter,
  }: {
    cursor?: string
    limit?: number
    sortDirection?: "asc" | "desc"
    statusFilter?: StatusFilter
  } = {},
) {
  const limitToCheck = Math.min(limit, 100)

  // Cursor format: "YYYY-MM-DD_UUID" (shape is validated at the API boundary)
  let cursorDate: string | undefined
  let cursorId: string | undefined

  if (cursor) {
    const parts = cursor.split("_")
    if (parts.length === 2) {
      cursorDate = parts[0]
      cursorId = parts[1]
    }
  }

  const data = await db
    .select()
    .from(interestProjection)
    .where(
      and(
        eq(interestProjection.userId, userId),
        statusFilter && statusFilter.length > 0
          ? inArray(interestProjection.status, statusFilter)
          : undefined,
        cursorDate && cursorId
          ? or(
              lt(interestProjection.transactionDate, cursorDate),
              and(
                eq(interestProjection.transactionDate, cursorDate),
                lt(interestProjection.id, cursorId),
              ),
            )
          : undefined,
      ),
    )
    .orderBy(
      sortDirection === "asc"
        ? asc(interestProjection.transactionDate)
        : desc(interestProjection.transactionDate),
      desc(interestProjection.id),
    )
    .limit(limitToCheck + 1)

  const hasNextPage = data.length > limitToCheck
  const items = hasNextPage ? data.slice(0, limitToCheck) : data

  const lastItem = items[items.length - 1]
  const nextCursor = lastItem ? `${lastItem.transactionDate}_${lastItem.id}` : null

  return {
    data: items,
    nextCursor: hasNextPage ? nextCursor : null,
    hasNextPage,
  }
}
