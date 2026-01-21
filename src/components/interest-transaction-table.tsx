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
import { getBankById } from "@/lib/banking/registry"
import { interestTransactionOptions } from "@/lib/interest-transaction-options"
import { cn } from "@/lib/utils"
import { type StatusFilter } from "@/server/projection-action"
import {
  IconArrowDown,
  IconArrowUp,
  IconArrowsSort,
  IconCheck,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconFilter,
  IconLoader,
  IconLoader3,
  IconRefresh,
} from "@tabler/icons-react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { type ColumnDef, type SortingState } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { Badge } from "./ui/badge"
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover"

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
            <IconArrowUp className="text-muted-foreground" />
          ) : column.getIsSorted() === "desc" ? (
            <IconArrowDown className="text-muted-foreground" />
          ) : (
            <IconArrowsSort className="text-muted-foreground" />
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
    accessorKey: "bankId",
    header: "Bank",
    cell: ({ row }) => {
      const bankId = row.getValue("bankId") as string
      const bank = getBankById(bankId)
      return <div className="text-muted-foreground">{bank?.name || bankId}</div>
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
    meta: {
      filterComponent: null, // Will be set dynamically
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

const STATUS_OPTIONS = [
  { value: "not_donated", label: "Not Donated" },
  { value: "partially_donated", label: "Partially Donated" },
  { value: "fully_donated", label: "Donated" },
] as const

function StatusFilterPopover({
  statusFilter,
  onStatusFilterChange,
}: {
  statusFilter: StatusFilter | undefined
  onStatusFilterChange: (filter: StatusFilter | undefined) => void
}) {
  const toggleStatus = (status: InterestProjection["status"]) => {
    if (!statusFilter || statusFilter.length === 0) {
      onStatusFilterChange([status])
    } else if (statusFilter.includes(status)) {
      const newFilter = statusFilter.filter(s => s !== status)
      onStatusFilterChange(newFilter.length > 0 ? newFilter : undefined)
    } else {
      onStatusFilterChange([...statusFilter, status])
    }
  }

  const isSelected = (status: InterestProjection["status"]) =>
    statusFilter?.includes(status) ?? false

  const hasActiveFilter = statusFilter && statusFilter.length > 0

  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button variant="ghost">
            Status
            <IconFilter
              className={cn({
                "text-primary": hasActiveFilter,
                "text-muted-foreground": !hasActiveFilter,
              })}
            />
            {hasActiveFilter && (
              <span className="flex size-4 items-center justify-center rounded-full bg-primary text-[10px] text-primary-foreground">
                {statusFilter.length}
              </span>
            )}
          </Button>
        }
      />
      <PopoverContent className="w-48 p-1" align="start">
        <div className="flex flex-col">
          {STATUS_OPTIONS.map(option => (
            <Button
              key={option.value}
              variant="ghost"
              className="justify-start gap-2 font-normal"
              onClick={() => toggleStatus(option.value)}
            >
              <span
                className={`flex size-4 items-center justify-center rounded border ${
                  isSelected(option.value)
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-muted-foreground/30"
                }`}
              >
                {isSelected(option.value) && <IconCheck className="size-3" />}
              </span>
              {option.label}
            </Button>
          ))}
          {hasActiveFilter && (
            <>
              <div className="my-1 h-px bg-border" />
              <Button
                variant="ghost"
                className="justify-start font-normal text-muted-foreground"
                onClick={() => onStatusFilterChange(undefined)}
              >
                Clear filter
              </Button>
            </>
          )}
        </div>
      </PopoverContent>
    </Popover>
  )
}

function InterestTransactionTable() {
  const [sorting, setSorting] = useState<SortingState>([{ id: "transactionDate", desc: true }])
  const [statusFilter, setStatusFilter] = useState<StatusFilter | undefined>(undefined)

  // Get sort direction for the query
  const sortDirection = sorting[0]?.desc ? "desc" : "asc"

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useInfiniteQuery(
    interestTransactionOptions(sortDirection, statusFilter),
  )

  // Flatten data
  const flatData = useMemo(() => data?.pages.flatMap(page => page.data) ?? [], [data])

  // Create columns with dynamic status filter header
  const columnsWithFilter = useMemo<ColumnDef<InterestProjection>[]>(
    () =>
      columns.map(col => {
        if ("accessorKey" in col && col.accessorKey === "status") {
          return {
            ...col,
            header: () => (
              <StatusFilterPopover
                statusFilter={statusFilter}
                onStatusFilterChange={setStatusFilter}
              />
            ),
          }
        }
        return col
      }),
    [statusFilter],
  )

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
          columns={columnsWithFilter}
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
