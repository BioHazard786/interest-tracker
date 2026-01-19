import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function parseDDMMYYYY(dateStr: string) {
  const [day, month, year] = dateStr.split(/[-/]/).map(Number)
  return new Date(year, month - 1, day)
}
