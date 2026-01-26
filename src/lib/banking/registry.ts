import { formatPNBDescription } from "./parsers/pnb"
import type { BankInfo, BankParser, DescriptionFormatter, FileFormat } from "./types"

export const banks: BankInfo[] = [
  {
    id: "in-pnb",
    name: "Punjab National Bank",
    formats: ["csv"],
    load: () => import("./parsers/pnb").then(m => m.PNBParser),
    descriptionFormatter: formatPNBDescription,
  },
  {
    id: "in-kotak",
    name: "Kotak Mahindra Bank",
    formats: ["pdf"],
    load: () => import("./parsers/kotak").then(m => m.KotakParser),
  },
  {
    id: "in-sbi",
    name: "State Bank of India",
    formats: ["xlsx"],
    load: () => import("./parsers/sbi").then(m => m.SBIParser),
  },
  {
    id: "in-idfc",
    name: "IDFC First Bank",
    formats: ["xlsx", "pdf"],
    load: () => import("./parsers/idfc").then(m => m.IDFCParser),
  },
]

export function getBankById(id: string): BankInfo | undefined {
  return banks.find(bank => bank.id === id)
}

export async function loadParser(bankId: string): Promise<BankParser> {
  const bank = getBankById(bankId)
  if (!bank) {
    throw new Error(`Unknown bank: ${bankId}`)
  }
  const parser = await bank.load()
  return "default" in parser ? parser.default : parser
}

export function getDescriptionFormatter(bankId: string): DescriptionFormatter {
  const bank = getBankById(bankId)
  if (bank?.descriptionFormatter) return bank.descriptionFormatter
  return (description: string | null) => description
}

export function getAcceptedFileTypes(formats: FileFormat[]): string {
  const mimeTypes: Record<FileFormat, string[]> = {
    csv: [".csv", "text/csv"],
    xlsx: [".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"],
    pdf: [".pdf", "application/pdf"],
  }
  return formats.flatMap(f => mimeTypes[f]).join(",")
}
