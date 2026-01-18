export interface Transaction {
  transactionId?: string
  transactionHash: string
  date: Date
  description: string
  amount: number
  type: "credit" | "debit"
  balance: number
}

export interface BankParser {
  id: string
  name: string
  canParse(content: string): boolean
  parse(content: string): Promise<Transaction[]>
}
