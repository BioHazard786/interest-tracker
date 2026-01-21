"use client"

import { StatsCard } from "@/components/stats-card"
import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
  InputGroupText,
} from "@/components/ui/input-group"
import { Label } from "@/components/ui/label"
import {
  ResponsiveDialog,
  ResponsiveDialogContent,
  ResponsiveDialogDescription,
  ResponsiveDialogFooter,
  ResponsiveDialogHeader,
  ResponsiveDialogTitle,
} from "@/components/ui/responsive-dialog"
import { addDonation } from "@/server/action"
import { IconMoodSmileBeam } from "@tabler/icons-react"
import { useRef, useState, useTransition } from "react"
import { toast } from "sonner"
import { Spinner } from "./ui/spinner"

interface DonationCardProps {
  totalDonated: number
}

export function DonationCard({
  totalDonated,
  amountLeft,
}: DonationCardProps & { amountLeft: number }) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isPending, startTransition] = useTransition()

  const handleDonate = () => {
    const value = parseFloat(inputRef.current?.value || "")

    // Reset error
    setError(null)

    if (isNaN(value) || value <= 0) {
      setError("Please enter a valid amount")
      return
    }

    if (value > amountLeft) {
      setError("Amount cannot exceed available balance")
      return
    }

    startTransition(async () => {
      const result = await addDonation(value)
      if (result.success) {
        toast.success("Donation added successfully")
        setOpen(false)
        inputRef.current!.value = ""
      } else {
        toast.error(result.message)
      }
    })
  }

  return (
    <>
      <StatsCard
        description="Donated"
        value={totalDonated}
        icon={<IconMoodSmileBeam />}
        action={
          <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
            Donate
          </Button>
        }
      />
      <ResponsiveDialog open={open} onOpenChange={setOpen}>
        <ResponsiveDialogContent>
          <ResponsiveDialogHeader>
            <ResponsiveDialogTitle>Add Donation</ResponsiveDialogTitle>
            <ResponsiveDialogDescription>
              Enter the amount you want to mark as donated.
            </ResponsiveDialogDescription>
          </ResponsiveDialogHeader>
          <div className="flex flex-col gap-2 p-4">
            <div className="flex flex-row items-center gap-4">
              <Label htmlFor="amount" className="text-right whitespace-nowrap">
                Amount
              </Label>
              <InputGroup className="flex-1">
                <InputGroupAddon>
                  <InputGroupText>₹</InputGroupText>
                </InputGroupAddon>
                <InputGroupInput ref={inputRef} placeholder="0.00" id="amount" type="number" />
                <InputGroupAddon align="inline-end">
                  <InputGroupText>INR</InputGroupText>
                </InputGroupAddon>
              </InputGroup>
            </div>
            {error && <p className="pl-16 text-right text-sm text-destructive">{error}</p>}
          </div>
          <ResponsiveDialogFooter>
            <Button onClick={handleDonate} disabled={isPending}>
              {isPending && <Spinner />}
              {isPending ? "Donating" : "Donate"}
            </Button>
          </ResponsiveDialogFooter>
        </ResponsiveDialogContent>
      </ResponsiveDialog>
    </>
  )
}
