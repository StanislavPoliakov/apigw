package msa.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
  val username: String,
  val firstName: String,
  val lastName: String,
  val profile: String
) {
  val id = hashCode()

  override fun toString(): String {
    return """
      username = $username
      firstName = $firstName
      lastName = $lastName
      profile = $profile
    """.trimIndent()
  }
}
