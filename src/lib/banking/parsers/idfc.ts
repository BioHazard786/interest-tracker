import type { BankParser, Transaction } from "@/lib/banking/types"
import { generateHash } from "@/lib/utils"
import pdf2md from "@opendocsg/pdf2md"
import readXlsxFile, { type Row, type Schema } from "read-excel-file"

// ============================================================================
// Common utilities
// ============================================================================

const INTEREST_DESCRIPTION = "monthly savings interest credit"

function isInterestTransaction(description: string): boolean {
  const normalized = description
    .replace(/[\r\n]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase()
  return normalized.includes(INTEREST_DESCRIPTION)
}

function parseAmount(amountStr: string): number {
  return Number.parseFloat(amountStr.replace(/,/g, ""))
}

// ============================================================================
// XLSX Parser
// ============================================================================

interface ParsedRow {
  transactionDate?: Date
  particulars?: string
  debit?: number
  credit?: number
  balance?: number
}

const schema: Schema<ParsedRow> = {
  transactionDate: {
    column: "Transaction Date",
    type: (value: unknown) => {
      if (value instanceof Date) return value
      if (typeof value === "string") return new Date(value)
      return undefined
    },
  },
  particulars: {
    column: "Particulars",
    type: String,
  },
  debit: {
    column: "Debit",
    type: (value: unknown) => {
      if (typeof value === "number") return value
      if (typeof value === "string") return Number.parseFloat(value.replace(/,/g, "")) || 0
      return 0
    },
  },
  credit: {
    column: "Credit",
    type: (value: unknown) => {
      if (typeof value === "number") return value
      if (typeof value === "string") return Number.parseFloat(value.replace(/,/g, "")) || 0
      return 0
    },
  },
  balance: {
    column: "Balance",
    type: (value: unknown) => {
      if (typeof value === "number") return value
      if (typeof value === "string") return Number.parseFloat(value.replace(/,/g, "")) || 0
      return 0
    },
  },
}

function findHeaderAndSlice(data: Row[]): Row[] {
  const headerIndex = data.findIndex(
    row =>
      row.some(
        cell => typeof cell === "string" && cell.toLowerCase().includes("transaction date"),
      ) && row.some(cell => typeof cell === "string" && cell.toLowerCase().includes("particulars")),
  )

  if (headerIndex === -1) {
    throw new Error("Could not find transaction header row in IDFC Statement")
  }

  return data.slice(headerIndex)
}

async function parseXlsx(file: File, userID: string | null): Promise<Transaction[]> {
  const transactions: Transaction[] = []

  const { rows } = await readXlsxFile<ParsedRow>(file, {
    schema,
    transformData: findHeaderAndSlice,
  })

  for (const row of rows) {
    if (!row.transactionDate || !row.particulars) continue
    if (Number.isNaN(row.transactionDate.getTime())) continue
    if (!isInterestTransaction(row.particulars)) continue

    const amount = (row.credit || 0) - (row.debit || 0)
    const balance = row.balance || 0

    const hashData = `${row.transactionDate.toISOString()}|${row.particulars}|${amount}|${balance}|${userID || ""}`
    const transactionHash = await generateHash(hashData)

    transactions.push({
      transactionHash,
      date: row.transactionDate,
      description: row.particulars,
      amount,
      type: amount >= 0 ? "credit" : "debit",
      balance,
    })
  }

  return transactions
}

// ============================================================================
// PDF Parser
// ============================================================================

const LINE_REGEX = /\r\n|\n/
const INTEREST_REGEX = /MONTHLY SAVINGS INTEREST CREDIT/i

// PDF format has description on one line and amount on next:
//   31-Aug-2025 31-Aug-2025 MONTHLY SAVINGS INTEREST CREDIT
//   ### 49.00 41,918.20
const DATE_LINE_REGEX =
  /^\s*(\d{2}-\w{3}-\d{4})\s+\d{2}-\w{3}-\d{4}\s+(MONTHLY SAVINGS INTEREST CREDIT)\s*$/i
const AMOUNT_LINE_REGEX = /^###\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$/

async function parsePdf(file: File, userID: string | null): Promise<Transaction[]> {
  const pdfBuffer = await file.arrayBuffer()
  const markdown = await pdf2md(pdfBuffer)

  const lines = markdown.split(LINE_REGEX)
  const transactions: Transaction[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // Skip lines that don't contain interest
    if (!INTEREST_REGEX.test(line)) continue

    const dateMatch = line.match(DATE_LINE_REGEX)
    if (!dateMatch) continue

    const [, dateStr, description] = dateMatch

    // Look for amount line within the next few lines (skip empty lines)
    let amountMatch: RegExpMatchArray | null = null
    let amountLineIndex = -1
    for (let j = i + 1; j < Math.min(i + 5, lines.length); j++) {
      const candidateLine = lines[j]
      if (!candidateLine || candidateLine.trim() === "") continue

      amountMatch = candidateLine.match(AMOUNT_LINE_REGEX)
      if (amountMatch) {
        amountLineIndex = j
        break
      }
    }

    if (!amountMatch || amountLineIndex === -1) continue

    const [, amountStr, balanceStr] = amountMatch

    const txnDate = new Date(dateStr)
    if (Number.isNaN(txnDate.getTime())) continue

    const amount = parseAmount(amountStr)
    const balance = parseAmount(balanceStr)

    const hashData = `${txnDate.toISOString()}|${description}|${amount}|${balance}|${userID || ""}`
    const transactionHash = await generateHash(hashData)

    transactions.push({
      transactionHash,
      date: txnDate,
      description: description.trim(),
      amount,
      type: "credit",
      balance,
    })

    // Skip to after the amount line we just processed
    i = amountLineIndex
  }

  return transactions
}

// ============================================================================
// Main Parser
// ============================================================================

export const IDFCParser: BankParser = {
  id: "in-idfc",
  name: "IDFC First Bank",

  async parse(file: File, userID: string): Promise<Transaction[]> {
    // Determine file type and use appropriate parser
    if (file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf")) {
      return parsePdf(file, userID)
    }

    // Default to XLSX parser
    return parseXlsx(file, userID)
  },
}
