export interface Transaction {
  transactionId?: string
  transactionHash: string
  date: Date
  description: string | null
  amount: number
  type: "credit" | "debit"
  balance: number
}

export interface BankParser {
  id: string
  name: string
  parse(file: File, userID: string): Promise<Transaction[]>
}

export type DescriptionFormatter = (description: string | null) => string | null

export type FileFormat = "csv" | "xlsx" | "pdf"

export interface BankInfo {
  id: string
  name: string
  formats: FileFormat[]
  load: () => Promise<{ default: BankParser } | BankParser>
  descriptionFormatter?: DescriptionFormatter
}
