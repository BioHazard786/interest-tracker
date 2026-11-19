import { db } from "@/db/drizzle"
import { interestTransaction } from "@/db/schema"
import { z } from "zod"

// Dates arrive as ISO strings over JSON, hence coerce.
const transactionSchema = z.object({
  bankId: z.string().min(1, "Bank ID is required"),
  transactions: z
    .array(
      z.object({
        transactionHash: z.string().min(1, "Transaction hash is required"),
        amount: z.number().transform(String),
        balance: z.number(),
        date: z.coerce.date().transform(d => d.toISOString().split("T")[0]),
        type: z.enum(["credit", "debit"]).optional().default("credit"),
        description: z.string().optional(),
        transactionId: z.string().optional(),
      }),
    )
    .min(1, "No transactions to sync"),
})

export async function syncTransactions(
  userId: string,
  input: unknown,
): Promise<{ success: boolean; message: string }> {
  const parsed = transactionSchema.safeParse(input)
  if (!parsed.success) {
    const message = parsed.error.issues.map(e => `${e.path.join(".")}: ${e.message}`).join(", ")
    return { success: false, message: `Validation error: ${message}` }
  }

  try {
    // Duplicates are skipped via the unique transactionHash.
    await db
      .insert(interestTransaction)
      .values(
        parsed.data.transactions.map(txn => ({
          userId,
          bankId: parsed.data.bankId,
          transactionHash: txn.transactionHash,
          amount: txn.amount,
          date: txn.date,
          type: txn.type,
          description: txn.description || null,
          transactionId: txn.transactionId || null,
        })),
      )
      .onConflictDoNothing()

    return { success: true, message: "Successfully synced transactions to database" }
  } catch (error) {
    return {
      success: false,
      message:
        error instanceof Error
          ? error.message
          : "An unexpected error occurred while syncing transactions",
    }
  }
}
