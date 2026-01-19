import { KotakParser } from "./parsers/kotak"
import { PNBParser } from "./parsers/pnb"
import { SBIParser } from "./parsers/sbi"
import type { BankParser } from "./types"

export const parsers: BankParser[] = [PNBParser, KotakParser, SBIParser]

export function detectParser(content: string | File): BankParser | null {
  for (const parser of parsers) {
    if (parser.canParse(content)) {
      return parser
    }
  }
  return null
}
