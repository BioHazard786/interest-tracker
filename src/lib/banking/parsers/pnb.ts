import { getUserID } from "@/lib/auth-client"
import type { BankParser, Transaction } from "@/lib/banking/types"
import { parseDDMMYYYY } from "@/lib/utils"
import { createHash } from "node:crypto"
import Papa from "papaparse"

const LINE_REGEX = /\r\n|\n/
export const PNBParser: BankParser = {
  id: "pnb-one",
  name: "Punjab National Bank (PNB ONE)",

  canParse(content: string | File): boolean {
    if (typeof content !== "string") return false
    return content.includes("PNB ONE") || content.includes("PUNB")
  },

  async parse(content: string | File): Promise<Transaction[]> {
    if (typeof content !== "string") throw new Error("PNB Parser only supports CSV content")
    const lines = content.split(LINE_REGEX)
    const headerLineIndex = lines.findIndex(
      line => line.includes("Txn No.") && line.includes("Txn Date"),
    )

    if (headerLineIndex === -1) {
      throw new Error("Could not find transaction header row (Txn No., Txn Date)")
    }

    const csvContent = lines.slice(headerLineIndex).join("\n")

    const result = Papa.parse(csvContent, {
      header: true,
      skipEmptyLines: true,
      dynamicTyping: false, // Keep as strings for precise parsing
    })

    const transactions: Transaction[] = []

    for (const row of result.data as any[]) {
      // Map PNB columns to Transaction interface
      // PNB Columns: Txn No., Txn Date, Description, Branch Name, Cheque No., Dr Amount, Cr Amount, Balance

      const description = row.Description || ""
      const descriptionLower = description.toLowerCase()

      // Filter logic: We want Interest entries.
      // Based on file analysis, interest entries look like:
      // "5188100100004899:Int.Pd:01-09-2025 to 30-11-2025" or similar containing "Int.Pd" or just "Interest"
      if (!(descriptionLower.includes("int.pd") || descriptionLower.includes("interest"))) {
        continue
      }

      const transactionId = row["Txn No."]?.toString() || undefined
      const txnDateStr = row["Txn Date"] || ""
      const txnDate = parseDDMMYYYY(txnDateStr)

      if (Number.isNaN(txnDate.getTime())) {
        continue // Skip invalid dates
      }

      const drAmount = Number.parseFloat(row["Dr Amount"] || "0")
      const crAmount = Number.parseFloat(row["Cr Amount"] || "0")
      const amount = crAmount - drAmount // Positive for Credit (Interest received), Negative for Debit
      const balance = Number.parseFloat(
        String(row.Balance || "0")
          .replace(" Cr.", "")
          .replace(" Dr.", "")
          .trim(),
      )

      // Generate unique transaction hash from key fields
      const userID = await getUserID()
      const hashData = `${txnDateStr}|${description}|${amount}|${balance}|${userID || ""}|${transactionId || ""}`
      const transactionHash = createHash("sha256").update(hashData).digest("hex")

      transactions.push({
        transactionId,
        transactionHash,
        date: txnDate,
        description,
        amount,
        type: amount >= 0 ? "credit" : "debit",
        balance,
      })
    }

    return transactions
  },
}
