import { user } from "@/db/auth-schema"
import { relations } from "drizzle-orm"
import {
  date,
  index,
  numeric,
  pgTable,
  text,
  timestamp,
  uniqueIndex,
  uuid,
} from "drizzle-orm/pg-core"

export const interestTransaction = pgTable(
  "interest_transaction",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),

    bankId: text("bank_id").notNull(),

    transactionId: text("transaction_id"),
    transactionHash: text("transaction_hash").notNull(),

    amount: numeric("amount", { precision: 12, scale: 2 }).notNull(),
    date: date("date").notNull(),

    type: text("type").$type<"credit" | "debit">().default("credit"),

    description: text("description"),

    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  table => [
    uniqueIndex("interest_hash_unique").on(table.transactionHash),
    index("interest_user_idx").on(table.userId),
    index("interest_date_idx").on(table.date),
    index("interest_bank_idx").on(table.bankId),
  ],
)

export const donation = pgTable(
  "donation",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),

    amount: numeric("amount", { precision: 12, scale: 2 }).notNull(),
    date: date("date").notNull(),

    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  table => [index("donation_user_idx").on(table.userId)],
)

export const interestRelations = relations(interestTransaction, ({ one }) => ({
  user: one(user, {
    fields: [interestTransaction.userId],
    references: [user.id],
  }),
}))

export const donationRelations = relations(donation, ({ one }) => ({
  user: one(user, {
    fields: [donation.userId],
    references: [user.id],
  }),
}))

export const interestProjection = pgTable(
  "interest_projection",
  {
    id: uuid("id").defaultRandom().primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),

    bankId: text("bank_id").notNull(),

    transactionHash: text("transaction_hash")
      .notNull()
      .references(() => interestTransaction.transactionHash, { onDelete: "cascade" }),

    transactionId: text("transaction_id"),

    amount: numeric("amount", { precision: 12, scale: 2 }).notNull(),
    donatedAmount: numeric("donated_amount", { precision: 12, scale: 2 }).notNull(),
    remainingAmount: numeric("remaining_amount", { precision: 12, scale: 2 }).notNull(),

    description: text("description"),
    transactionDate: date("transaction_date").notNull(),
    donationAt: date("donation_at"),

    status: text("status").$type<"not_donated" | "partially_donated" | "fully_donated">().notNull(),

    updatedAt: timestamp("updated_at").defaultNow().notNull(),
  },
  table => [
    uniqueIndex("projection_txn_unique").on(table.transactionHash),
    index("projection_date_idx").on(table.transactionDate),
    index("projection_user_idx").on(table.userId),
    index("projection_status_idx").on(table.status),
    index("projection_bank_idx").on(table.bankId),
  ],
)

export const interestProjectionRelations = relations(interestProjection, ({ one }) => ({
  user: one(user, {
    fields: [interestProjection.userId],
    references: [user.id],
  }),
  transaction: one(interestTransaction, {
    fields: [interestProjection.transactionHash],
    references: [interestTransaction.transactionHash],
  }),
}))

export type InterestProjection = typeof interestProjection.$inferSelect
