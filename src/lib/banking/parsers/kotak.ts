import type { BankParser, Transaction } from "@/lib/banking/types"
import { generateHash } from "@/lib/utils"
import pdf2md from "@opendocsg/pdf2md"

const LINE_REGEX = /\r\n|\n/
const INTEREST_REGEX = /Int\.Pd:/i

// Matches lines like: " 131 31 Mar 2025 Int.Pd:6347718530:01-01-2025 to 31-03-2025 12.00 2,704.39"
// Format: # DATE DESCRIPTION AMOUNT BALANCE
const TRANSACTION_LINE_REGEX =
  /^\s*(\d+)\s+(\d{1,2}\s+\w{3}\s+\d{4})\s+(Int\.Pd:[^\s]+(?:\s+to\s+\d{2}-\d{2}-\d{4})?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$/i

function parseAmount(amountStr: string): number {
  return Number.parseFloat(amountStr.replace(/,/g, ""))
}

export const KotakParser: BankParser = {
  id: "in-kotak",
  name: "Kotak Mahindra Bank",

  async parse(file: File, userID: string): Promise<Transaction[]> {
    const pdfBuffer = await file.arrayBuffer()
    const markdown = await pdf2md(pdfBuffer)

    const lines = markdown.split(LINE_REGEX)
    const transactions: Transaction[] = []

    for (const line of lines) {
      // Skip lines that don't contain interest
      if (!INTEREST_REGEX.test(line)) continue

      const match = line.match(TRANSACTION_LINE_REGEX)
      if (!match) continue

      const [, , dateStr, description, amountStr, balanceStr] = match

      const txnDate = new Date(dateStr)
      if (Number.isNaN(txnDate.getTime())) continue

      const amount = parseAmount(amountStr)
      const balance = parseAmount(balanceStr)

      // Interest is always a credit (deposit)
      const hashData = `${dateStr}|${description}|${amount}|${balance}|${userID || ""}`
      const transactionHash = await generateHash(hashData)

      transactions.push({
        transactionHash,
        date: txnDate,
        description: description.trim(),
        amount,
        type: "credit",
        balance,
      })
    }

    return transactions
  },
}
