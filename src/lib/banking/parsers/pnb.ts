import type { BankParser, DescriptionFormatter, Transaction } from "@/lib/banking/types"
import { generateHash, parseDDMMYYYY } from "@/lib/utils"
import Papa from "papaparse"

const LINE_REGEX = /\r\n|\n/
const INTEREST_REGEX = /int\.pd|interest/i
const INTEREST_RANGE_REGEX =
  /int\.\s*\.?pd[:\s]*([0-9]{2}-[0-9]{2}-[0-9]{4})\s*(?:to|-)\s*([0-9]{2}-[0-9]{2}-[0-9]{4})/i

const humanDateFormatter = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
})

export const formatPNBDescription: DescriptionFormatter = (description: string | null) => {
  if (!description) return description
  const rangeMatch = description.match(INTEREST_RANGE_REGEX)
  if (rangeMatch) {
    const [, fromRaw, toRaw] = rangeMatch
    const fromDate = parseDDMMYYYY(fromRaw)
    const toDate = parseDDMMYYYY(toRaw)

    if (!Number.isNaN(fromDate.getTime()) && !Number.isNaN(toDate.getTime())) {
      return `Interest received from the bank from ${humanDateFormatter.format(fromDate)} to ${humanDateFormatter.format(toDate)}`
    }
  }

  return description
}

export const PNBParser: BankParser = {
  id: "in-pnb",
  name: "Punjab National Bank",

  async parse(file: File, userID: string): Promise<Transaction[]> {
    const content = await file.text()

    const lines = content.split(LINE_REGEX)
    const headerLineIndex = lines.findIndex(
      line => line.includes("Txn No.") && line.includes("Txn Date"),
    )

    if (headerLineIndex === -1) {
      throw new Error("Could not find transaction header row (Txn No., Txn Date)")
    }

    const result = Papa.parse<Record<string, string>>(lines.slice(headerLineIndex).join("\n"), {
      header: true,
      skipEmptyLines: true,
    })

    const transactions: Transaction[] = []

    for (const row of result.data) {
      const description = row.Description || ""

      if (!INTEREST_REGEX.test(description)) continue

      const txnDateStr = row["Txn Date"] || ""
      const txnDate = parseDDMMYYYY(txnDateStr)

      if (Number.isNaN(txnDate.getTime())) continue

      const transactionId = row["Txn No."] || undefined
      const drAmount = Number.parseFloat(row["Dr Amount"] || "0")
      const crAmount = Number.parseFloat(row["Cr Amount"] || "0")
      const amount = crAmount - drAmount
      const balance = Number.parseFloat((row.Balance || "0").replace(/ (Cr|Dr)\.$/, "").trim())

      const hashData = `${txnDateStr}|${description}|${amount}|${balance}|${userID || ""}|${transactionId || ""}`
      const transactionHash = await generateHash(hashData)

      const humanDescription = formatPNBDescription(description)

      transactions.push({
        transactionId,
        transactionHash,
        date: txnDate,
        description: humanDescription,
        amount,
        type: amount >= 0 ? "credit" : "debit",
        balance,
      })
    }

    return transactions
  },
}
