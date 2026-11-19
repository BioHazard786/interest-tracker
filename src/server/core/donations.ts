import { db } from "@/db/drizzle"
import { donation } from "@/db/schema"
import { z } from "zod"
import { getDashboardStats } from "./stats"

const donationSchema = z.object({
  amount: z.number().positive("Amount must be positive"),
})

export async function addDonation(
  userId: string,
  amount: unknown,
): Promise<{ success: boolean; message: string }> {
  const validated = donationSchema.safeParse({ amount })
  if (!validated.success) {
    return { success: false, message: "Invalid amount" }
  }

  // The web UI checks this too, but the API is the trust boundary now that there are two clients.
  const { totalInterest, totalDonated } = await getDashboardStats(userId)
  if (Math.round(validated.data.amount * 100) > Math.round((totalInterest - totalDonated) * 100)) {
    return { success: false, message: "Amount cannot exceed available balance" }
  }

  try {
    await db.insert(donation).values({
      userId,
      amount: validated.data.amount.toString(),
      date: new Date().toISOString().split("T")[0],
    })
    return { success: true, message: "Donation added" }
  } catch {
    return { success: false, message: "Failed to add donation" }
  }
}
