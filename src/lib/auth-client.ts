import { createAuthClient } from "better-auth/react";

export const authClient = createAuthClient({
  baseURL: process.env.BETTER_AUTH_URL || "http://localhost:3000",
});

export const getUserID = async (): Promise<string | null> => {
  const { data: session } = await authClient.getSession();
  return session?.user.id || null;
};
