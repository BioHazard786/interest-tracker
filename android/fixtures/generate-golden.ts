// Runs the REAL web parsers over fixtures/statements and records what they produce, so the Kotlin
// parsers can be tested against byte-identical hashes. See fixtures/README.md.
import { readFileSync, writeFileSync, readdirSync } from "node:fs"
import { join } from "node:path"
import { DOMParser } from "@xmldom/xmldom"
import { KotakParser } from "@/lib/banking/parsers/kotak"
import { IDFCParser } from "@/lib/banking/parsers/idfc"
import { PNBParser } from "@/lib/banking/parsers/pnb"
import { SBIParser } from "@/lib/banking/parsers/sbi"
import { hashInputs } from "@/lib/utils"

// Run with the working directory set to android/fixtures (see generate.sh).
const here = process.cwd()
const dir = join(here, "statements")
const USER_ID = "test-user-0001"

// read-excel-file's browser build (what the web app uses) expects the browser's DOMParser.
;(globalThis as any).DOMParser = DOMParser

const cases: { file: string; source?: string; parser: any; type: string }[] = [
  { file: "pnb.csv", parser: PNBParser, type: "text/csv" },
  { file: "sbi.xlsx", parser: SBIParser, type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
  { file: "idfc.xlsx", parser: IDFCParser, type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
  // PDFs: the fixture is the pdf2md markdown itself (see pdf2md-stub.ts), named .pdf for the parsers.
  { file: "idfc.pdf", source: "idfc.md.txt", parser: IDFCParser, type: "application/pdf" },
  { file: "kotak.pdf", source: "kotak.md.txt", parser: KotakParser, type: "application/pdf" },
  // Altered copies of real statements (anonymize_real.py); the PDFs go through pdf2md first (pdf-to-md.ts).
  { file: "real-pnb.csv", parser: PNBParser, type: "text/csv" },
  { file: "real-kotak.csv", parser: KotakParser, type: "text/csv" },
  { file: "real-kotak.pdf", source: "real-kotak.md.txt", parser: KotakParser, type: "application/pdf" },
  { file: "real-idfc.xlsx", parser: IDFCParser, type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" },
  { file: "real-idfc.pdf", source: "real-idfc.md.txt", parser: IDFCParser, type: "application/pdf" },
]

const out: unknown[] = []
for (const c of cases) {
  const bytes = readFileSync(join(dir, c.source ?? c.file))
  const file = new File([bytes], c.file, { type: c.type })
  hashInputs.length = 0
  const txns = await c.parser.parse(file, USER_ID)
  out.push({
    file: c.file,
    bankId: c.parser.id,
    userId: USER_ID,
    transactions: txns.map((t, i) => ({
      hashInput: hashInputs[i],
      hash: t.transactionHash,
      transactionId: t.transactionId ?? null,
      date: t.date.toISOString(),
      description: t.description,
      amount: t.amount,
      type: t.type,
      balance: t.balance,
    })),
  })
}
writeFileSync(join(here, "expected.json"), JSON.stringify({ timezone: process.env.TZ, cases: out }, null, 2) + "\n")
console.log(`TZ=${process.env.TZ}:`, out.map((o: any) => `${o.file}=${o.transactions.length}`).join(", "))
