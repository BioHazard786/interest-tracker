import { parseDDMMYYYY } from "@/lib/utils"
// @ts-ignore
import { getUserID } from "@/lib/auth-client"
import type { BankParser, Transaction } from "@/lib/banking/types"
import { createHash } from "node:crypto"
import readXlsxFile from "read-excel-file"

export const SBIParser: BankParser = {
  id: "sbi",
  name: "State Bank of India",

  canParse(content: string | File): boolean {
    if (typeof content === "string") return false
    // Basic check for Excel file type or extension
    // content is a File object here
    return (
      content.name.toLowerCase().endsWith(".xlsx") ||
      content.type === "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
  },

  async parse(content: string | File): Promise<Transaction[]> {
    if (typeof content === "string") {
      throw new Error("SBI Parser requires an Excel file (XLSX)")
    }

    const rows = await readXlsxFile(content)
    const transactions: Transaction[] = []
    const userID = await getUserID()

    // Find header row: contains "Txn Date" or "Date" AND "Debit" AND "Credit"
    const headerIndex = rows.findIndex(
      (row: any[]) =>
        row.some(cell => typeof cell === "string" && cell.toLowerCase().includes("debit")) &&
        row.some(cell => typeof cell === "string" && cell.toLowerCase().includes("credit")),
    )

    if (headerIndex === -1) {
      throw new Error("Could not find transaction header row in SBI Statement")
    }

    // Data starts from the next row
    // Column Mapping based on inspection:
    // 0: Txn Date
    // 1: Description / Details
    // 2: Ref No (can be null)
    // 3: Debit
    // 4: Credit
    // 5: Balance

    for (let i = headerIndex + 1; i < rows.length; i++) {
      const row = rows[i]
      // Ensure row has enough columns
      if (!row || row.length < 6) continue

      const description = (row[1] || "").toString()
      // Normalize: remove newlines, collapse multiple spaces
      const descNormalized = description
        .replace(/[\r\n]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase()

      // Filter for Interest
      // "INTERES T CREDIT" -> "interes t credit" (matches user case)
      if (
        !descNormalized.includes("interest") &&
        !descNormalized.includes("int.pd") &&
        !descNormalized.includes("interes t credit")
      ) {
        continue
      }

      const dateStr = row[0]
      let date: Date
      if (typeof dateStr === "string") {
        date = parseDDMMYYYY(dateStr as string)
      } else {
        continue
      }

      if (Number.isNaN(date.getTime())) continue

      const debit =
        typeof row[3] === "number"
          ? row[3]
          : Number.parseFloat((row[3] || "0").toString().replace(/,/g, ""))
      const credit =
        typeof row[4] === "number"
          ? row[4]
          : Number.parseFloat((row[4] || "0").toString().replace(/,/g, ""))

      // SBI Debit/Credit are exclusive usually, but let's be safe
      const amount = (credit || 0) - (debit || 0)

      const balance =
        typeof row[5] === "number"
          ? row[5]
          : Number.parseFloat((row[5] || "0").toString().replace(/,/g, ""))

      const transactionId = (row[2] || "").toString() || undefined // Ref No

      const hashData = `${date.toISOString()}|${description}|${amount}|${balance}|${userID || ""}|${transactionId || ""}`
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
