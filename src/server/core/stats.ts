import { db } from "@/db/drizzle"
import { donation, interestTransaction } from "@/db/schema"
import { and, eq, sql } from "drizzle-orm"

export async function getLastTransactionDateByBank(
  userId: string,
): Promise<Record<string, string | null>> {
  const results = await db
    .select({
      bankId: interestTransaction.bankId,
      lastDate: sql<string>`max(${interestTransaction.date})`,
    })
    .from(interestTransaction)
    .where(eq(interestTransaction.userId, userId))
    .groupBy(interestTransaction.bankId)

  return Object.fromEntries(results.map(r => [r.bankId, r.lastDate]))
}

export async function getDashboardStats(userId: string) {
  const [interestResult, donationResult] = await Promise.all([
    db
      .select({ total: sql<string>`sum(${interestTransaction.amount})` })
      .from(interestTransaction)
      .where(and(eq(interestTransaction.userId, userId), eq(interestTransaction.type, "credit"))),
    db
      .select({ total: sql<string>`sum(${donation.amount})` })
      .from(donation)
      .where(eq(donation.userId, userId)),
  ])

  return {
    totalInterest: Number.parseFloat(interestResult[0]?.total || "0"),
    totalDonated: Number.parseFloat(donationResult[0]?.total || "0"),
  }
}
