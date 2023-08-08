package msa.models

import org.jetbrains.exposed.dao.id.IntIdTable

object ProfilesTable : IntIdTable() {
  val username = varchar("username", LENGTH_DEFAULT)
  val firstName = varchar("firstName", LENGTH_DEFAULT)
  val lastName = varchar("lastName", LENGTH_DEFAULT)
  val profile = varchar("profile", LENGTH_DEFAULT)
}

private const val LENGTH_DEFAULT = 256