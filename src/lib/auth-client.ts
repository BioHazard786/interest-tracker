import { createAuthClient } from "better-auth/react"

export const authClient = createAuthClient()

export const getUserID = async (): Promise<string | null> => {
  const { data: session } = await authClient.getSession()
  return session?.user.id || null
}
