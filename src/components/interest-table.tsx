import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useIsMobile } from "@/hooks/use-mobile"
import type { Transaction } from "@/lib/banking/types"
import { cn } from "@/lib/utils"

interface InterestTableProps {
  transactions: Transaction[]
}

export function InterestTable({ transactions }: InterestTableProps) {
  const totalInterest = transactions.reduce((acc, curr) => acc + curr.amount, 0)
  const isMobile = useIsMobile()

  return (
    <ScrollArea className={cn("h-70 w-full", isMobile && "px-4")}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Date</TableHead>
            <TableHead>
              {transactions[0]?.transactionId ? "Transaction ID" : "Transaction Hash"}
            </TableHead>
            <TableHead className="text-right">Amount</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {transactions.length === 0 ? (
            <TableRow>
              <TableCell className="h-24 text-center" colSpan={3}>
                No interest transactions found.
              </TableCell>
            </TableRow>
          ) : (
            transactions.map((txn, index) => (
              <TableRow
                className="odd:bg-muted/50 hover:bg-transparent odd:hover:bg-muted/50"
                key={`${index}-${txn.date}`}
              >
                <TableCell className="whitespace-nowrap">
                  {new Intl.DateTimeFormat("en-US", {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                  }).format(txn.date)}
                </TableCell>
                <TableCell className="font-mono text-muted-foreground">
                  {txn.transactionId || txn.transactionHash.slice(0, 12)}
                </TableCell>
                <TableCell className="text-right">
                  {new Intl.NumberFormat("en-IN", {
                    style: "currency",
                    currency: "INR",
                  }).format(txn.amount)}
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell colSpan={2}>Total Interest</TableCell>
            <TableCell className="text-right">
              {new Intl.NumberFormat("en-IN", {
                style: "currency",
                currency: "INR",
              }).format(totalInterest)}
            </TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    </ScrollArea>
  )
}
