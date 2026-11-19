import { db } from "@/db/drizzle"
import { donation, interestProjection, interestTransaction } from "@/db/schema"
import { asc, eq, sql } from "drizzle-orm"

// Rebuilds interest_projection for one user by applying donations to interest FIFO.
export async function reconcileUserInterests(userId: string) {
  // 1. Fetch all inputs
  const [allInterests, allDonations] = await Promise.all([
    db
      .select()
      .from(interestTransaction)
      .where(eq(interestTransaction.userId, userId))
      .orderBy(asc(interestTransaction.date)), // FIFO
    db.select().from(donation).where(eq(donation.userId, userId)).orderBy(asc(donation.date)), // FIFO
  ])

  if (allInterests.length === 0) return

  // 2. FIFO Waterfall with Donation Tracking
  const projectionUpdates: any[] = []

  let donationIdx = 0
  let currentDonationRemaining = allDonations.length > 0 ? Number(allDonations[0].amount) : 0

  for (const interest of allInterests) {
    const interestAmount = Number(interest.amount)
    let needed = interestAmount
    let covered = 0
    let lastContributingDonationDate: string | null = null

    // Consume donations until interest is covered or donations run out
    while (needed > 0 && donationIdx < allDonations.length) {
      const take = Math.min(needed, currentDonationRemaining)

      covered += take
      needed -= take
      currentDonationRemaining -= take

      // Track the date of the donation we just used
      lastContributingDonationDate = allDonations[donationIdx].date

      // If current donation exhausted, move to next
      if (currentDonationRemaining === 0) {
        donationIdx++
        if (donationIdx < allDonations.length) {
          currentDonationRemaining = Number(allDonations[donationIdx].amount)
        }
      }
    }

    const remainingAmount = interestAmount - covered

    let status: "not_donated" | "partially_donated" | "fully_donated" = "not_donated"
    if (remainingAmount === 0) {
      status = "fully_donated"
    } else if (covered > 0) {
      status = "partially_donated"
    }

    projectionUpdates.push({
      userId: userId,
      bankId: interest.bankId,
      transactionHash: interest.transactionHash,
      transactionId: interest.transactionId,
      amount: interest.amount,
      donatedAmount: covered.toFixed(2),
      remainingAmount: remainingAmount.toFixed(2),
      description: interest.description,
      transactionDate: interest.date,
      donationAt: lastContributingDonationDate,
      status: status,
      updatedAt: new Date(),
    })
  }

  // 3. Batch Upsert to Projection
  if (projectionUpdates.length > 0) {
    await db
      .insert(interestProjection)
      .values(projectionUpdates)
      .onConflictDoUpdate({
        target: interestProjection.transactionHash,
        set: {
          bankId: sql`excluded.bank_id`,
          donatedAmount: sql`excluded.donated_amount`,
          remainingAmount: sql`excluded.remaining_amount`,
          status: sql`excluded.status`,
          updatedAt: sql`excluded.updated_at`,
          donationAt: sql`excluded.donation_at`,
          description: sql`excluded.description`,
          transactionDate: sql`excluded.transaction_date`,
          transactionId: sql`excluded.transaction_id`,
        },
      })
  }
}
