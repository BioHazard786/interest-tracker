import { PNBParser } from "./parsers/pnb";
import type { BankParser } from "./types";

export const parsers: BankParser[] = [PNBParser];

export function detectParser(content: string): BankParser | null {
  for (const parser of parsers) {
    if (parser.canParse(content)) {
      return parser;
    }
  }
  return null;
}
