package com.biohazard786.interesttracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Stats(val totalInterest: Double = 0.0, val totalDonated: Double = 0.0) {
  val amountLeft get() = totalInterest - totalDonated
}

@Serializable
data class ProjectionItem(
  val id: String,
  val bankId: String,
  val transactionHash: String,
  val transactionId: String? = null,
  val amount: String,
  val donatedAmount: String,
  val remainingAmount: String,
  val description: String? = null,
  val transactionDate: String,
  val donationAt: String? = null,
  val status: String,
  val updatedAt: String? = null,
)

@Serializable
data class ProjectionPage(
  val data: List<ProjectionItem>,
  val nextCursor: String? = null,
  val hasNextPage: Boolean = false,
)

enum class DonationStatus(val apiValue: String, val label: String) {
  NotDonated("not_donated", "Pending"),
  PartiallyDonated("partially_donated", "Partial"),
  FullyDonated("fully_donated", "Donated");

  companion object {
    fun from(value: String) = entries.firstOrNull { it.apiValue == value } ?: NotDonated
  }
}

@Serializable internal data class DonationBody(val amount: Double)

@Serializable internal data class ApiMessage(val success: Boolean = true, val message: String = "")

@Serializable internal data class IdTokenBody(val token: String, val nonce: String)

@Serializable internal data class SocialSignInBody(val provider: String, val idToken: IdTokenBody)

@Serializable internal data class SignInResponse(val token: String? = null)

@Serializable internal data class SessionUser(val id: String)

@Serializable internal data class SessionResponse(val user: SessionUser? = null)

@Serializable
internal data class SyncTxn(
  val transactionHash: String,
  val amount: Double,
  val balance: Double,
  /** "YYYY-MM-DD": the date printed on the statement. */
  val date: String,
  val type: String,
  val description: String,
  val transactionId: String? = null,
)

@Serializable internal data class SyncBody(val bankId: String, val transactions: List<SyncTxn>)
