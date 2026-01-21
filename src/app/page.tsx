import { Dashboard } from "@/components/dashboard"
import { SignIn } from "@/components/sign-in"
import { auth } from "@/lib/auth"
import { headers } from "next/headers"

export default async function Home() {
  const session = await auth.api.getSession({
    headers: await headers(),
  })

  if (!session) {
    return <SignIn />
  }

  return <Dashboard userID={session.user.id} />
}
