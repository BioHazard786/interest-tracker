import type { BankParser, Transaction } from "@/lib/banking/types"
import { generateHash, parseDDMMYYYY } from "@/lib/utils"
import readXlsxFile, { type Row, type Schema } from "read-excel-file"

interface ParsedRow {
  date?: Date
  details?: string
  debit?: number
  credit?: number
  balance?: number
}

// Schema for SBI bank statement parsing
// Keys are property names, 'column' specifies the Excel header to match
const schema: Schema<ParsedRow> = {
  date: {
    column: "Date",
    type: (value: unknown) => {
      if (value instanceof Date) return value
      if (typeof value === "string") return parseDDMMYYYY(value)
      return undefined
    },
  },
  details: {
    column: "Details",
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

// Check if description contains interest-related keywords
function isInterestTransaction(description: string): boolean {
  const normalized = description
    .replace(/[\r\n]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase()

  return normalized.includes("interes t credit")
}

// Find header row and slice data - all in one transformData call
function findHeaderAndSlice(data: Row[]): Row[] {
  const headerIndex = data.findIndex(
    row =>
      row.some(cell => typeof cell === "string" && cell.toLowerCase().includes("debit")) &&
      row.some(cell => typeof cell === "string" && cell.toLowerCase().includes("credit")),
  )

  if (headerIndex === -1) {
    throw new Error("Could not find transaction header row in SBI Statement")
  }

  return data.slice(headerIndex)
}

export const SBIParser: BankParser = {
  id: "in-sbi",
  name: "State Bank of India",

  async parse(file: File, userID: string): Promise<Transaction[]> {
    const transactions: Transaction[] = []

    // Single read with schema + transformData that finds header and slices
    const { rows } = await readXlsxFile<ParsedRow>(file, {
      schema,
      transformData: findHeaderAndSlice,
    })

    // Process each parsed row
    for (const row of rows) {
      if (!row.date || !row.details) continue
      if (Number.isNaN(row.date.getTime())) continue
      if (!isInterestTransaction(row.details)) continue

      const amount = (row.credit || 0) - (row.debit || 0)
      const balance = row.balance || 0

      const hashData = `${row.date.toISOString()}|${row.details}|${amount}|${balance}|${userID || ""}`
      const transactionHash = await generateHash(hashData)

      transactions.push({
        transactionHash,
        date: row.date,
        description: row.details,
        amount,
        type: amount >= 0 ? "credit" : "debit",
        balance,
      })
    }

    return transactions
  },
}
