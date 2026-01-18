import InterestTransactionTable from "@/components/interest-transaction-table"
import { SignIn } from "@/components/sign-in"
import { auth } from "@/lib/auth"
import { headers } from "next/headers"

async function Page() {
  const session = await auth.api.getSession({
    headers: await headers(),
  })

  if (!session) {
    return <SignIn />
  }

  return (
    <div className="min-h-screen space-y-8 p-6">
      <main className="container mx-auto max-w-4xl space-y-8">
        <InterestTransactionTable />
      </main>
    </div>
  )
}

export default Page
