import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function parseDDMMYYYY(dateStr: string) {
  const [day, month, year] = dateStr.split(/[-/]/).map(Number)
  return new Date(year, month - 1, day)
}

export function parseDDMMMYYYY(dateStr: string) {
  const months: Record<string, number> = {
    jan: 0,
    feb: 1,
    mar: 2,
    apr: 3,
    may: 4,
    jun: 5,
    jul: 6,
    aug: 7,
    sep: 8,
    oct: 9,
    nov: 10,
    dec: 11,
  }

  const [day, monthStr, year] = dateStr.split("-")
  const month = months[monthStr.toLowerCase()]
  return new Date(Number(year), month, Number(day))
}

export async function generateHash(data: string): Promise<string> {
  const msgBuffer = new TextEncoder().encode(data)
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgBuffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map(b => b.toString(16).padStart(2, "0")).join("")
}
