import type { BankParser, Transaction } from "@/lib/banking/types"
import { generateHash } from "@/lib/utils"
import pdf2md from "@opendocsg/pdf2md"
import Papa from "papaparse"

const LINE_REGEX = /\r\n|\n/
const INTEREST_REGEX = /Int\.Pd:/i

// Matches lines like: " 131 31 Mar 2025 Int.Pd:1234567890:01-01-2025 to 31-03-2025 12.00 2,704.39"
// Format: # DATE DESCRIPTION AMOUNT BALANCE
const TRANSACTION_LINE_REGEX =
  /^\s*(\d+)\s+(\d{1,2}\s+\w{3}\s+\d{4})\s+(Int\.Pd:[^\s]+(?:\s+to\s+\d{2}-\d{2}-\d{4})?)\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$/i

function parseAmount(amountStr: string): number {
  return Number.parseFloat(amountStr.replace(/,/g, ""))
}

// The description both formats share, e.g. "Int.Pd:1234567890:01-01-2025 to 31-03-2025". The CSV and the
// PDF must hash the same text so a statement uploaded in either format is stored once.
const DESCRIPTION_REGEX = /Int\.Pd:[^\s]+(?:\s+to\s+\d{2}-\d{2}-\d{4})?/i

async function toTransaction(
  dateStr: string,
  description: string,
  amountStr: string,
  balanceStr: string,
  userID: string | null,
): Promise<Transaction | null> {
  const txnDate = new Date(dateStr)
  if (Number.isNaN(txnDate.getTime())) return null

  const amount = parseAmount(amountStr)
  const balance = parseAmount(balanceStr)
  if (Number.isNaN(amount) || Number.isNaN(balance)) return null

  // Interest is always a credit (deposit)
  const hashData = `${dateStr}|${description}|${amount}|${balance}|${userID || ""}`
  return {
    transactionHash: await generateHash(hashData),
    date: txnDate,
    description: description.trim(),
    amount,
    type: "credit",
    balance,
  }
}

async function parsePdf(file: File, userID: string | null): Promise<Transaction[]> {
  const markdown = await pdf2md(await file.arrayBuffer())
  const transactions: Transaction[] = []

  for (const line of markdown.split(LINE_REGEX)) {
    // Skip lines that don't contain interest
    if (!INTEREST_REGEX.test(line)) continue

    const match = line.match(TRANSACTION_LINE_REGEX)
    if (!match) continue

    const [, , dateStr, description, amountStr, balanceStr] = match
    const txn = await toTransaction(dateStr, description, amountStr, balanceStr, userID)
    if (txn) transactions.push(txn)
  }

  return transactions
}

// Columns: #, Date, Description, Chq/Ref. No., Withdrawal (Dr.), Deposit (Cr.), Balance
async function parseCsv(file: File, userID: string | null): Promise<Transaction[]> {
  const lines = (await file.text()).split(LINE_REGEX)
  const headerLineIndex = lines.findIndex(
    line => line.includes("Description") && line.includes("Deposit (Cr.)"),
  )
  if (headerLineIndex === -1) {
    throw new Error("Could not find transaction header row (Date, Description, Deposit (Cr.))")
  }

  const result = Papa.parse<Record<string, string>>(lines.slice(headerLineIndex).join("\n"), {
    header: true,
    skipEmptyLines: true,
  })

  const transactions: Transaction[] = []
  for (const row of result.data) {
    const description = row.Description?.match(DESCRIPTION_REGEX)?.[0]
    if (!description) continue

    const txn = await toTransaction(
      row.Date || "",
      description,
      row["Deposit (Cr.)"] || "",
      row.Balance || "",
      userID,
    )
    if (txn) transactions.push(txn)
  }

  return transactions
}

export const KotakParser: BankParser = {
  id: "in-kotak",
  name: "Kotak Mahindra Bank",

  async parse(file: File, userID: string): Promise<Transaction[]> {
    return file.name.toLowerCase().endsWith(".csv")
      ? parseCsv(file, userID)
      : parsePdf(file, userID)
  },
}
