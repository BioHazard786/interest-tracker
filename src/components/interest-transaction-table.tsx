"use client"

import { Badge } from "@/components/ui/badge"
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
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { type InterestProjection } from "@/db/schema"
import { getBankById, getDescriptionFormatter } from "@/lib/banking/registry"
import { interestTransactionOptions } from "@/lib/interest-transaction-options"
import { cn, formatCurrency, formatDate } from "@/lib/utils"
import { type StatusFilter } from "@/server/projection-action"
import {
  IconArrowDown,
  IconArrowUp,
  IconArrowsSort,
  IconCheck,
  IconChevronDown,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconFilter,
  IconLayoutColumns,
  IconLoader,
  IconLoader3,
  IconRefresh,
} from "@tabler/icons-react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { type ColumnDef, type SortingState, type VisibilityState } from "@tanstack/react-table"
import { useMemo, useState } from "react"

const COL_ID_TO_NAME = {
  description: "Description",
  status: "Status",
  bankId: "Bank",
  transactionHash: "Transaction Hash",
  transactionId: "Transaction ID",
  amount: "Amount",
  donatedAmount: "Donated Amount",
  remainingAmount: "Remaining Amount",
  transactionDate: "Date",
  donationAt: "Donation At",
  updatedAt: "Updated At",
}

// Define columns
export const columns: ColumnDef<InterestProjection>[] = [
  {
    accessorKey: "transactionDate",
    enableHiding: true,
    header: ({ column }) => (
      <Button variant="ghost" onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}>
        Date
        {column.getIsSorted() === "asc" ? (
          <IconArrowUp className="text-muted-foreground" />
        ) : column.getIsSorted() === "desc" ? (
          <IconArrowDown className="text-muted-foreground" />
        ) : (
          <IconArrowsSort className="text-muted-foreground" />
        )}
      </Button>
    ),
    cell: ({ row }) => (
      <div className="pl-4">{formatDate(new Date(row.getValue("transactionDate")))}</div>
    ),
  },
  {
    accessorKey: "status",
    enableHiding: true,
    header: "Status",
    cell: ({ row }) => (
      <Badge variant="outline" className="px-1.5 text-muted-foreground">
        {getStatusBadge(row.getValue("status"))}
      </Badge>
    ),
    meta: {
      filterComponent: null,
    },
  },
  {
    accessorKey: "donationAt",
    enableHiding: true,
    header: "Donation At",
    cell: ({ row }) => (
      <div className="text-muted-foreground">
        {row.getValue("donationAt") ? formatDate(new Date(row.getValue("donationAt"))) : "-"}
      </div>
    ),
  },
  {
    accessorKey: "bankId",
    enableHiding: true,
    header: "Bank",
    cell: ({ row }) => {
      const bankId = row.getValue("bankId") as string
      const bank = getBankById(bankId)
      return <div className="text-muted-foreground">{bank?.name || bankId}</div>
    },
  },
  {
    accessorKey: "amount",
    enableHiding: true,
    header: () => <div className="pr-4 text-right">Amount</div>,
    cell: ({ row }) => {
      const amount = Number(row.getValue("amount"))
      return <div className="pr-4 text-right font-medium">{formatCurrency(amount)}</div>
    },
  },
  {
    accessorKey: "donatedAmount",
    enableHiding: true,
    header: () => <div className="pr-4 text-right">Donated</div>,
    cell: ({ row }) => {
      const amount = Number(row.getValue("donatedAmount"))
      return <div className="pr-4 text-right text-muted-foreground">{formatCurrency(amount)}</div>
    },
  },
  {
    accessorKey: "remainingAmount",
    enableHiding: true,
    header: () => <div className="pr-4 text-right">Remaining</div>,
    cell: ({ row }) => {
      const amount = Number(row.getValue("remainingAmount"))
      return <div className="pr-4 text-right text-muted-foreground">{formatCurrency(amount)}</div>
    },
  },
  {
    accessorKey: "description",
    enableHiding: true,
    header: "Description",
    cell: ({ row }) => {
      const description = (row.getValue("description") as string | null) ?? "-"
      return (
        <div className="max-w-xs truncate text-muted-foreground" title={description}>
          {description}
        </div>
      )
    },
  },
  {
    accessorKey: "transactionId",
    enableHiding: true,
    header: "Transaction ID",
    cell: ({ row }) => {
      const id = row.getValue("transactionId") as string | null
      return (
        <div className="font-mono text-muted-foreground" title={id ?? ""}>
          {id ?? "-"}
        </div>
      )
    },
  },
  {
    accessorKey: "transactionHash",
    enableHiding: true,
    header: "Transaction Hash",
    cell: ({ row }) => {
      const hash = row.getValue("transactionHash") as string
      return (
        <div className="max-w-xs truncate font-mono text-muted-foreground" title={hash}>
          {hash}
        </div>
      )
    },
  },
  {
    accessorKey: "updatedAt",
    enableHiding: true,
    header: "Updated",
    cell: ({ row }) => {
      const updatedAt = row.getValue("updatedAt") as Date | string
      return <div className="text-muted-foreground">{formatDate(new Date(updatedAt))}</div>
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
  const [selectedProjection, setSelectedProjection] = useState<InterestProjection | null>(null)
  const [isSheetOpen, setIsSheetOpen] = useState(false)
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({
    bankId: false,
    donatedAmount: false,
    remainingAmount: false,
    transactionId: false,
    transactionHash: false,
    description: false,
    updatedAt: false,
  })

  // Get sort direction for the query
  const sortDirection = sorting[0]?.desc ? "desc" : "asc"

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useInfiniteQuery(
    interestTransactionOptions(sortDirection, statusFilter),
  )
  // Flatten data
  const flatData = useMemo(
    () =>
      data?.pages.flatMap(page =>
        page.data.map(transaction => ({
          ...transaction,
          description: getDescriptionFormatter(transaction.bankId)(transaction.description),
        })),
      ) ?? [],
    [data],
  )

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

  const handleRowClick = (row: InterestProjection) => {
    setSelectedProjection(row)
    setIsSheetOpen(true)
  }

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
          onRowClick={row => handleRowClick(row.original)}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          toolbar={table => (
            <div className="flex items-center gap-2">
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button variant="outline" size="sm">
                      <IconLayoutColumns />
                      <span className="hidden lg:inline">Customize Columns</span>
                      <span className="lg:hidden">Columns</span>
                      <IconChevronDown />
                    </Button>
                  }
                />
                <DropdownMenuContent align="end" className="w-56">
                  {table
                    .getAllColumns()
                    .filter(
                      column => typeof column.accessorFn !== "undefined" && column.getCanHide(),
                    )
                    .map(column => (
                      <DropdownMenuCheckboxItem
                        key={column.id}
                        className="capitalize"
                        checked={column.getIsVisible()}
                        onCheckedChange={value => column.toggleVisibility(Boolean(value))}
                      >
                        {COL_ID_TO_NAME[column.id as keyof typeof COL_ID_TO_NAME] || column.id}
                      </DropdownMenuCheckboxItem>
                    ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}
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
      <Sheet
        open={isSheetOpen}
        onOpenChange={open => {
          setIsSheetOpen(open)
          if (!open) setSelectedProjection(null)
        }}
      >
        <SheetContent
          side="right"
          className="w-full max-w-xl border-l bg-background/95 sm:max-w-lg"
        >
          {selectedProjection && (
            <>
              <SheetHeader className="gap-3 border-b p-4">
                <div className="flex flex-wrap items-center gap-2 text-xs font-medium text-muted-foreground">
                  <Badge variant="outline" className="gap-1">
                    {getStatusBadge(selectedProjection.status)}
                  </Badge>
                  <Badge variant="secondary" className="gap-1">
                    {getBankById(selectedProjection.bankId)?.name ?? selectedProjection.bankId}
                  </Badge>
                  {selectedProjection.transactionId && (
                    <Badge variant="outline" className="font-mono">
                      #{selectedProjection.transactionId}
                    </Badge>
                  )}
                </div>
                <SheetTitle className="text-xl">
                  {formatCurrency(Number(selectedProjection.amount))}
                </SheetTitle>
                <SheetDescription>
                  Interest recorded on {formatDate(new Date(selectedProjection.transactionDate))}
                </SheetDescription>
              </SheetHeader>

              <ScrollArea className="h-[calc(100dvh-180px)]">
                <div className="flex flex-col gap-4 p-4 pt-0">
                  <div className="grid gap-4 sm:grid-cols-3">
                    {[
                      { label: "Total", value: selectedProjection.amount },
                      { label: "Donated", value: selectedProjection.donatedAmount },
                      { label: "Remaining", value: selectedProjection.remainingAmount },
                    ].map(item => (
                      <div key={item.label} className="rounded-lg border bg-muted/30 p-3">
                        <div className="text-xs text-muted-foreground">{item.label}</div>
                        <div className="text-base font-semibold">
                          {formatCurrency(Number(item.value))}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="space-y-6 rounded-xl border bg-muted/10 p-5">
                    <div className="flex flex-wrap items-center justify-between gap-2 text-sm font-medium text-foreground">
                      <span>Transaction details</span>
                      <span className="text-xs text-muted-foreground">
                        Updated {formatDate(new Date(selectedProjection.updatedAt))}
                      </span>
                    </div>
                    <dl className="flex flex-col gap-3 text-sm">
                      <DetailItem
                        label="Donation Date"
                        value={
                          selectedProjection.donationAt
                            ? formatDate(new Date(selectedProjection.donationAt))
                            : "Pending"
                        }
                      />
                      {selectedProjection.transactionId && (
                        <DetailItem
                          label="Transaction ID"
                          value={selectedProjection.transactionId}
                          monospaced
                        />
                      )}
                      <DetailItem
                        label="Description"
                        value={selectedProjection.description || "No description provided"}
                        className="sm:col-span-2"
                      />
                    </dl>
                  </div>
                </div>
              </ScrollArea>
            </>
          )}
        </SheetContent>
      </Sheet>
    </Card>
  )
}

export default InterestTransactionTable

function DetailItem({
  label,
  value,
  monospaced = false,
  className,
}: {
  label: string
  value: string
  monospaced?: boolean
  className?: string
}) {
  return (
    <div className={cn("space-y-1", className)}>
      <dt className="text-xs tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd
        className={cn(
          "text-sm leading-relaxed text-foreground",
          monospaced && "font-mono break-all",
        )}
      >
        {value}
      </dd>
    </div>
  )
}
