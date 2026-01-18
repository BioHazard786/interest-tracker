"use server"

import { db } from "@/db/drizzle"
import { donation, interestProjection, interestTransaction } from "@/db/schema"
import { auth } from "@/lib/auth"
import type { Transaction as ClientTransaction } from "@/lib/banking/types"
import { and, asc, eq, sql } from "drizzle-orm"
import { revalidatePath } from "next/cache"
import { headers } from "next/headers"
import { after } from "next/server"
import { z } from "zod"

// Validation schema for incoming transactions
const transactionSchema = z.array(
  z.object({
    transactionHash: z.string().min(1, "Transaction hash is required"),
    amount: z.number().transform(String),
    balance: z.number(),
    date: z.date().transform(d => d.toISOString().split("T")[0]),
    type: z.enum(["credit", "debit"]).optional().default("credit"),
    description: z.string().optional(),
    transactionId: z.string().optional(),
  }),
)

export async function syncTransactionsToDb(
  transactions: ClientTransaction[],
): Promise<{ success: boolean; message: string }> {
  try {
    // Get the current user session
    const session = await auth.api.getSession({
      headers: await headers(),
    })

    if (!session) {
      return {
        success: false,
        message: "You must be logged in to sync transactions",
      }
    }

    // Validate input data
    const validatedTransactions = transactionSchema.safeParse(transactions)

    if (!validatedTransactions.success) {
      console.log(validatedTransactions.error)
      return {
        success: false,
        message: "No transactions to sync",
      }
    }

    // Insert transactions into database
    // Filtering out duplicates based on txnHash
    await db
      .insert(interestTransaction)
      .values(
        validatedTransactions.data.map(txn => ({
          userId: session.user.id,
          transactionHash: txn.transactionHash,
          amount: txn.amount,
          date: txn.date,
          type: txn.type,
          description: txn.description || null,
          transactionId: txn.transactionId || null,
        })),
      )
      .onConflictDoNothing()

    revalidatePath("/")

    after(() => reconcileUserInterests(session.user.id))

    return {
      success: true,
      message: "Successfully synced transactions to database",
    }
  } catch (error) {
    if (error instanceof z.ZodError) {
      const errorMessages = error.issues.map(e => `${e.path.join(".")}: ${e.message}`).join(", ")
      return {
        success: false,
        message: `Validation error: ${errorMessages}`,
      }
    }

    if (error instanceof Error) {
      return {
        success: false,
        message: error.message,
      }
    }

    return {
      success: false,
      message: "An unexpected error occurred while syncing transactions",
    }
  }
}

export async function getDashboardStats() {
  const session = await auth.api.getSession({
    headers: await headers(),
  })

  if (!session) {
    return {
      totalInterest: 0,
      totalDonated: 0,
    }
  }

  // Calculate total interest and total donated in parallel
  const [interestResult, donationResult] = await Promise.all([
    db
      .select({
        total: sql<string>`sum(${interestTransaction.amount})`,
      })
      .from(interestTransaction)
      .where(
        and(
          eq(interestTransaction.userId, session.user.id),
          eq(interestTransaction.type, "credit"),
        ),
      ),
    db
      .select({
        total: sql<string>`sum(${donation.amount})`,
      })
      .from(donation)
      .where(eq(donation.userId, session.user.id)),
  ])

  const totalInterest = Number.parseFloat(interestResult[0]?.total || "0")
  const totalDonated = Number.parseFloat(donationResult[0]?.total || "0")

  return {
    totalInterest,
    totalDonated,
  }
}

const donationSchema = z.object({
  amount: z.number().positive("Amount must be positive"),
})

export async function addDonation(amount: number) {
  const session = await auth.api.getSession({
    headers: await headers(),
  })

  if (!session) {
    return {
      success: false,
      message: "Unauthorized",
    }
  }

  const validated = donationSchema.safeParse({ amount })
  if (!validated.success) {
    return {
      success: false,
      message: "Invalid amount",
    }
  }

  try {
    await db.insert(donation).values({
      userId: session.user.id,
      amount: validated.data.amount.toString(),
      date: new Date().toISOString().split("T")[0],
    })

    revalidatePath("/")
    after(() => reconcileUserInterests(session.user.id))
    return { success: true, message: "Donation added" }
  } catch (error) {
    return { success: false, message: "Failed to add donation" }
  }
}

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
