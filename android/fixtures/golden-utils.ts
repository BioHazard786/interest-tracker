// Stand-in for "@/lib/utils" during golden generation: same exports, but records every hash input.
import { createHash } from "node:crypto"
export { parseDDMMYYYY, parseDDMMMYYYY } from "../../src/lib/utils"

export const hashInputs: string[] = []

export async function generateHash(data: string): Promise<string> {
  hashInputs.push(data)
  return createHash("sha256").update(data).digest("hex")
}
