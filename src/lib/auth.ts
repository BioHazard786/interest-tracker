import { betterAuth } from "better-auth";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
import { nextCookies } from "better-auth/next-js";
import { authSchema } from "@/db/auth-schema";
import { db } from "@/db/drizzle";

export const auth = betterAuth({
  database: drizzleAdapter(db, {
    provider: "pg",
    schema: authSchema,
  }),
  socialProviders: {
    google: {
      clientId: process.env.GOOGLE_CLIENT_ID as string,
      clientSecret: process.env.GOOGLE_CLIENT_SECRET as string,
      enabled: true,
    },
  },
  session: {
    // Very long session expiry (1 year)
    expiresIn: 60 * 60 * 24 * 365, // 365 days
    // Don't update session frequently to avoid unnecessary DB writes
    updateAge: 60 * 60 * 24 * 30, // Update every 30 days
    // Use secure cookies
    cookieCache: {
      enabled: true,
      maxAge: 60 * 60 * 24 * 365, // 365 days
    },
  },
  plugins: [nextCookies()],
});
