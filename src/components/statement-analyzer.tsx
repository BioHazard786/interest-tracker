"use client"

import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import {
  ResponsiveDialog,
  ResponsiveDialogContent,
  ResponsiveDialogDescription,
  ResponsiveDialogFooter,
  ResponsiveDialogHeader,
  ResponsiveDialogTitle,
} from "@/components/ui/responsive-dialog"
import { Spinner } from "@/components/ui/spinner"
import { formatBytes, useFileUpload } from "@/hooks/use-file-upload"
import { detectParser } from "@/lib/banking/registry"
import type { BankParser, Transaction } from "@/lib/banking/types"
import { syncTransactionsToDb } from "@/server/action"
import {
  IconAlertCircle,
  IconFileDescription,
  IconFileTypeCsv,
  IconFileTypeXls,
  IconRefresh,
  IconTrash,
  IconUpload,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useMemo, useState, useTransition } from "react"
import { toast } from "sonner"
import { InterestTable } from "./interest-table"

export function StatementAnalyzer() {
  const [isProcessing, setIsProcessing] = useState(false)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [isDialogOpen, setIsDialogOpen] = useState(false)
  const [processError, setProcessError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const maxFiles = 10

  const [
    { files, isDragging, errors: uploadErrors },
    {
      handleDragEnter,
      handleDragLeave,
      handleDragOver,
      handleDrop,
      openFileDialog,
      removeFile,
      clearFiles,
      getInputProps,
    },
  ] = useFileUpload({
    accept: ".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    maxFiles,
    multiple: true,
  })

  const fileCount = useMemo(() => files.length, [files.length])
  const hasFiles = useMemo(() => fileCount > 0, [fileCount])
  const canAddMore = useMemo(() => fileCount < maxFiles, [fileCount])

  const getFileIcon = useCallback((file: { file: File | { type: string; name: string } }) => {
    const fileType = file.file instanceof File ? file.file.type : file.file.type
    const fileName = file.file instanceof File ? file.file.name : file.file.name

    if (fileType.includes("csv") || fileName.endsWith(".csv")) {
      return <IconFileTypeCsv className="size-4 opacity-60" />
    }
    if (fileType.includes("xlsx") || fileName.endsWith(".xlsx")) {
      return <IconFileTypeXls className="size-4 opacity-60" />
    }
    return <IconFileDescription className="size-4 opacity-60" />
  }, [])

  const handleProcessFiles = useCallback(async () => {
    if (files.length === 0) {
      return
    }

    setIsProcessing(true)
    setProcessError(null)

    // Filter to only File instances before processing
    const validFiles = files.filter(fileObj => fileObj.file instanceof File)

    try {
      // Process all files in parallel for better performance
      const results = await Promise.all(
        validFiles.map(async fileObj => {
          const file = fileObj.file as File
          let parser: BankParser | null = null
          let content: string | File

          if (file.name.toLowerCase().endsWith(".xlsx")) {
            content = file
            parser = detectParser(content)
          } else {
            content = await file.text()
            parser = detectParser(content)
          }

          if (!parser) {
            throw new Error(`Could not detect a supported bank format for file: ${file.name}`)
          }

          return parser.parse(content)
        }),
      )

      const allTransactions = results.flat()

      // Remove duplicate transactions based on unique hash
      const uniqueTransactions = Array.from(
        new Map(allTransactions.map(txn => [txn.transactionHash, txn])).values(),
      )

      if (uniqueTransactions.length === 0) {
        setProcessError("No interest transactions found in the uploaded files.")
        setIsProcessing(false)
        return
      }

      uniqueTransactions.sort((a, b) => b.date.getTime() - a.date.getTime())

      setTransactions(uniqueTransactions)
      setIsDialogOpen(true)
      setIsProcessing(false)
      // biome-ignore lint/suspicious/noExplicitAny: BS
    } catch (err: any) {
      setProcessError(err.message || "Failed to process files")
      console.error(err)
      setIsProcessing(false)
    }
  }, [files])

  const handleTransactionSync = useCallback(() => {
    startTransition(async () => {
      console.log(transactions)
      const { success, message } = await syncTransactionsToDb(transactions)
      if (success) {
        clearFiles()
        setTransactions([])
        toast.success(message)
      } else {
        toast.error(message)
      }
      setIsDialogOpen(false)
    })
  }, [transactions, clearFiles])

  return (
    <div className="mx-auto w-full max-w-xl space-y-6">
      <div className="flex flex-col gap-2">
        <div
          className="flex flex-col items-center rounded-xl border border-dashed border-input p-4 transition-colors has-[input:focus]:border-ring has-[input:focus]:ring-[3px] has-[input:focus]:ring-ring/50 data-[dragging=true]:bg-accent/50"
          data-dragging={isDragging || undefined}
          data-files={files.length > 0 || undefined}
          onDragEnter={handleDragEnter}
          onDragLeave={handleDragLeave}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
        >
          <input {...getInputProps()} aria-label="Upload files" className="sr-only" />

          {hasFiles ? (
            <div className="flex w-full flex-col gap-3">
              <div className="flex items-center justify-between gap-2">
                <h3 className="truncate text-sm font-medium">Uploaded Files ({fileCount})</h3>
                <Button onClick={clearFiles} size="sm" variant="outline">
                  <IconTrash aria-hidden="true" className="-ms-0.5 size-3.5 opacity-60" />
                  Remove all
                </Button>
              </div>
              <div className="w-full space-y-2">
                {files.map(file => (
                  <div
                    className="flex items-center justify-between gap-2 rounded-lg border bg-background p-2 pe-3"
                    key={file.id}
                  >
                    <div className="flex items-center gap-3 overflow-hidden">
                      <div className="flex aspect-square size-10 shrink-0 items-center justify-center rounded border">
                        {getFileIcon(file)}
                      </div>
                      <div className="flex min-w-0 flex-col gap-0.5">
                        <p className="truncate text-[13px] font-medium">
                          {file.file instanceof File ? file.file.name : file.file.name}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {formatBytes(file.file instanceof File ? file.file.size : file.file.size)}
                        </p>
                      </div>
                    </div>

                    <Button
                      aria-label="Remove file"
                      className="-me-2 size-8 text-muted-foreground/80 hover:bg-transparent hover:text-foreground"
                      onClick={() => removeFile(file.id)}
                      size="icon"
                      variant="ghost"
                    >
                      <IconX aria-hidden="true" className="size-4" />
                    </Button>
                  </div>
                ))}

                {canAddMore && (
                  <Button
                    className="mt-2 w-full"
                    onClick={openFileDialog}
                    size="sm"
                    variant="outline"
                  >
                    <IconUpload aria-hidden="true" className="-ms-1 opacity-60" />
                    Add more
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <Empty className="p-3 md:p-6">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <IconFileDescription />
                </EmptyMedia>
                <EmptyTitle>Statement Analyzer</EmptyTitle>
                <EmptyDescription>
                  Upload your bank statement to extract interest transactions.
                </EmptyDescription>
              </EmptyHeader>
              <EmptyContent>
                <Button onClick={openFileDialog} size="sm" variant="outline">
                  <IconUpload aria-hidden="true" />
                  Select Files
                </Button>
              </EmptyContent>
            </Empty>
          )}
        </div>

        {uploadErrors.length > 0 && (
          <div className="flex items-center gap-1 text-xs text-destructive" role="alert">
            <IconAlertCircle className="size-3 shrink-0" />
            <span>{uploadErrors[0]}</span>
          </div>
        )}
      </div>

      <div className="flex justify-end">
        <Button
          className="w-auto"
          disabled={!hasFiles || isProcessing}
          onClick={handleProcessFiles}
        >
          {isProcessing && <Spinner className="animate-spin" />}
          Analyze Statements
        </Button>
      </div>

      {processError && (
        <div className="flex items-center gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          <span className="font-bold">Error:</span> {processError}
        </div>
      )}

      <ResponsiveDialog onOpenChange={setIsDialogOpen} open={isDialogOpen}>
        <ResponsiveDialogContent
          className="flex max-h-[80vh] max-w-4xl flex-col"
          disableCloseButton={isPending}
        >
          <ResponsiveDialogHeader>
            <ResponsiveDialogTitle>Analysis Results</ResponsiveDialogTitle>
            <ResponsiveDialogDescription>
              Found {transactions.length} interest transactions.
            </ResponsiveDialogDescription>
          </ResponsiveDialogHeader>
          <InterestTable transactions={transactions} />
          <ResponsiveDialogFooter>
            <Button disabled={isPending} onClick={handleTransactionSync} size="sm" type="submit">
              <IconRefresh className={isPending ? "animate-spin direction-reverse" : ""} />
              Sync
            </Button>
          </ResponsiveDialogFooter>
        </ResponsiveDialogContent>
      </ResponsiveDialog>
    </div>
  )
}
