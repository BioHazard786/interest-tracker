"use client"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { DataTable } from "@/components/ui/data-table"
import { type InterestProjection } from "@/db/schema"
import { interestTransactionOptions } from "@/lib/interest-transaction-options"
import {
  IconArrowDown,
  IconArrowUp,
  IconArrowsSort,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconLoader,
  IconLoader3,
  IconRefresh,
} from "@tabler/icons-react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { type ColumnDef, type SortingState } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { Badge } from "./ui/badge"

// Define columns
export const columns: ColumnDef<InterestProjection>[] = [
  {
    accessorKey: "transactionDate",
    header: ({ column }) => {
      return (
        <Button
          variant="ghost"
          onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
        >
          Date
          {column.getIsSorted() === "asc" ? (
            <IconArrowUp className="ml-2 size-4" />
          ) : column.getIsSorted() === "desc" ? (
            <IconArrowDown className="ml-2 size-4" />
          ) : (
            <IconArrowsSort className="ml-2 size-4" />
          )}
        </Button>
      )
    },
    cell: ({ row }) => {
      return (
        <div className="pl-4">
          {new Intl.DateTimeFormat("en-US", {
            month: "short",
            day: "numeric",
            year: "numeric",
          }).format(new Date(row.getValue("transactionDate")))}
        </div>
      )
    },
  },
  {
    accessorKey: "transactionId",
    header: "Transaction ID",
    cell: ({ row }) => {
      const id = row.getValue("transactionId") as string | null
      return (
        <div className="font-mono text-muted-foreground" title={id || ""}>
          {id || "-"}
        </div>
      )
    },
    enableHiding: true,
    meta: {
      className: "hidden md:table-cell",
    },
  },

  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => {
      return (
        <Badge variant="outline" className="px-1.5 text-muted-foreground">
          {getStatusBadge(row.getValue("status"))}
        </Badge>
      )
    },
  },
  {
    accessorKey: "amount",
    header: () => <div className="pr-4 text-right">Amount</div>,
    cell: ({ row }) => {
      const amount = Number.parseFloat(row.getValue("amount"))
      const formatted = new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
      }).format(amount)

      return <div className="pr-4 text-right font-medium">{formatted}</div>
    },
  },
]

function getStatusBadge(status: InterestProjection["status"]): React.ReactNode {
  switch (status) {
    case "fully_donated":
      return (
        <>
          <IconCircleCheckFilled className="fill-green-500 dark:fill-green-400" />
          Donated
        </>
      )
    case "partially_donated":
      return (
        <>
          <IconLoader />
          Partially Donated
        </>
      )
    case "not_donated":
      return (
        <>
          <IconCircleXFilled className="fill-red-500 dark:fill-red-400" />
          Not Donated
        </>
      )
  }
}

function InterestTransactionTable() {
  const [sorting, setSorting] = useState<SortingState>([{ id: "transactionDate", desc: true }])

  // Get sort direction for the query
  const sortDirection = sorting[0]?.desc ? "desc" : "asc"

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useInfiniteQuery(
    interestTransactionOptions(sortDirection),
  )

  // Flatten data
  const flatData = useMemo(() => data?.pages.flatMap(page => page.data) ?? [], [data])

  if (isLoading) {
    return (
      <Card className="border-none bg-transparent py-0 shadow-none md:py-6">
        <CardHeader className="px-0 md:px-6">
          <CardTitle className="text-center">Interest Transactions</CardTitle>
          <CardDescription className="text-center">
            A list of all interest transactions.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-center px-0 py-12 md:px-6">
          <IconLoader3 stroke={1} className="size-6 animate-spin" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-none bg-transparent py-0 shadow-none md:py-6">
      <CardHeader className="px-0 md:px-6">
        <CardTitle className="text-center">Interest Transactions</CardTitle>
        <CardDescription className="text-center">
          A list of all interest transactions.
        </CardDescription>
      </CardHeader>
      <CardContent className="px-0 md:px-6">
        <DataTable
          columns={columns}
          data={flatData}
          sorting={sorting}
          onSortingChange={setSorting}
        />
      </CardContent>
      {hasNextPage && (
        <CardFooter className="justify-center">
          <Button
            variant="outline"
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            size="sm"
          >
            <IconRefresh className={isFetchingNextPage ? "animate-spin direction-reverse" : ""} />
            Load More
          </Button>
        </CardFooter>
      )}
    </Card>
  )
}

export default InterestTransactionTable
