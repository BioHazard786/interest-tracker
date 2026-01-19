import { getUserID } from "@/lib/auth-client"
import type { BankParser, Transaction } from "@/lib/banking/types"
import { createHash } from "node:crypto"
import Papa from "papaparse"

const LINE_REGEX = /\r\n|\n/

export const KotakParser: BankParser = {
  id: "kotak-811",
  name: "Kotak Mahindra Bank",

  canParse(content: string | File): boolean {
    if (typeof content !== "string") return false
    // Kotak statements often contain "KKBK" (IFSC) or "Kotak" headers
    // Based on the file provided, it has "IFSC Code KKBK..."
    return content.includes("KKBK") || content.includes("Kotak")
  },

  async parse(content: string | File): Promise<Transaction[]> {
    if (typeof content !== "string") throw new Error("Kotak Parser only supports CSV content")
    const lines = content.split(LINE_REGEX)
    // The header row in the provided CSV is: #,Date,Description,Chq/Ref. No.,Withdrawal (Dr.),Deposit (Cr.),Balance
    const headerLineIndex = lines.findIndex(
      line =>
        line.includes("Date") && line.includes("Description") && line.includes("Withdrawal (Dr.)"),
    )

    if (headerLineIndex === -1) {
      throw new Error("Could not find transaction header row (Date, Description, Withdrawal)")
    }

    const csvContent = lines.slice(headerLineIndex).join("\n")

    const result = Papa.parse(csvContent, {
      header: true,
      skipEmptyLines: true,
      dynamicTyping: false,
    })

    const transactions: Transaction[] = []
    const userID = await getUserID()

    for (const row of result.data as any[]) {
      // Columns: #, Date, Description, Chq/Ref. No., Withdrawal (Dr.), Deposit (Cr.), Balance

      const description = row.Description || ""

      // Filter logic: We want Interest entries.
      // Kotak Interest entries usually look like "Int.Pd:..."
      if (!description.toLowerCase().includes("int.pd")) {
        continue
      }

      // Date format: "01 Jan 2025"
      const dateStr = row.Date || ""
      const date = new Date(dateStr)
      if (Number.isNaN(date.getTime())) {
        continue // Skip invalid dates
      }

      const withdrawal = Number.parseFloat((row["Withdrawal (Dr.)"] || "0").replace(/,/g, ""))
      const deposit = Number.parseFloat((row["Deposit (Cr.)"] || "0").replace(/,/g, ""))

      // Interest is usually a credit (Deposit)
      // Note: withdrawals are positive numbers in the column, so we subtract them if calculating net
      // But for interest, it should be a deposit.

      let amount = 0
      if (deposit > 0) {
        amount = deposit
      } else if (withdrawal > 0) {
        amount = -withdrawal
      }

      const balance = Number.parseFloat((row.Balance || "0").replace(/,/g, ""))

      const transactionId = row["Chq/Ref. No."] || undefined

      // Generate unique transaction hash
      // Using similar logic to PNB parser for consistency
      const hashData = `${dateStr}|${description}|${amount}|${balance}|${userID || ""}|${transactionId || ""}`
      const transactionHash = createHash("sha256").update(hashData).digest("hex")

      transactions.push({
        transactionId,
        transactionHash,
        date,
        description,
        amount,
        type: amount >= 0 ? "credit" : "debit",
        balance,
      })
    }

    return transactions
  },
}
