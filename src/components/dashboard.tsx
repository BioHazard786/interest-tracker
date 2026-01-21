import { DonationCard } from "@/components/donation-card"
import { StatementAnalyzer } from "@/components/statement-analyzer"
import { StatsCard } from "@/components/stats-card"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { getDashboardStats, getLastTransactionDateByBank } from "@/server/action"
import { IconMoodSadDizzy, IconMoodSick } from "@tabler/icons-react"
import Link from "next/link"

export async function Dashboard({ userID }: { userID: string }) {
  const [{ totalInterest, totalDonated }, lastTransactionDates] = await Promise.all([
    getDashboardStats(),
    getLastTransactionDateByBank(),
  ])
  const amountLeft = totalInterest - totalDonated

  return (
    <div className="min-h-screen space-y-8 p-6">
      <main className="container mx-auto max-w-4xl">
        <Card className="border-none bg-transparent py-0 shadow-none md:py-6">
          <CardHeader className="px-0 md:px-6">
            <CardTitle className="text-center">Dashboard</CardTitle>
            <CardDescription className="text-center">
              A dashboard to manage your interest transactions.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-8 px-0 md:px-6">
            <div className="grid gap-4 md:grid-cols-3">
              <StatsCard
                description="Total Interest"
                value={totalInterest}
                icon={<IconMoodSick />}
                action={
                  <Button
                    size="sm"
                    variant="outline"
                    render={<Link href="/interest/transactions">View</Link>}
                    nativeButton={false}
                  />
                }
              />

              <DonationCard totalDonated={totalDonated} amountLeft={amountLeft} />

              <StatsCard description="Amount Left" value={amountLeft} icon={<IconMoodSadDizzy />} />
            </div>

            <StatementAnalyzer userID={userID} lastTransactionDates={lastTransactionDates} />
          </CardContent>
        </Card>
      </main>
    </div>
  )
}
