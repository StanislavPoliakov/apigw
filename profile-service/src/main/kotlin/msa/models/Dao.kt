package msa.models

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface Dao {
  suspend fun add(profile: UserProfile): Int

  suspend fun getProfile(username: String): UserProfile?

  suspend fun delete(username: String): Boolean

  suspend fun update(profile: UserProfile): Boolean

  suspend fun getAll(): List<UserProfile>?
}

class DaoImpl : Dao {
  private fun resultRowToUserEntity(row: ResultRow): UserProfile = UserProfile(
    username = row[ProfilesTable.username],
    firstName = row[ProfilesTable.firstName],
    lastName = row[ProfilesTable.lastName],
    profile = row[ProfilesTable.profile]
  )

  override suspend fun add(profile: UserProfile): Int = dbQuery {
    ProfilesTable.insertAndGetId {
      it[id] = profile.id
      it[username] = profile.username
      it[firstName] = profile.firstName
      it[lastName] = profile.lastName
      it[ProfilesTable.profile] = profile.profile
    }.value
  }

  override suspend fun getProfile(username: String): UserProfile? = dbQuery {
    ProfilesTable
      .select { ProfilesTable.username eq username }
      .map(::resultRowToUserEntity)
      .singleOrNull()
  }

  override suspend fun getAll(): List<UserProfile>? = dbQuery {
    ProfilesTable
      .selectAll()
      .map(::resultRowToUserEntity)
      .takeUnless { it.isEmpty() }
  }

  override suspend fun delete(username: String): Boolean = dbQuery {
    ProfilesTable.deleteWhere { ProfilesTable.username eq username } > 0
  }

  override suspend fun update(profile: UserProfile): Boolean = dbQuery {
    ProfilesTable.update({ ProfilesTable.username eq profile.username }) {
      it[firstName] = profile.firstName
      it[lastName] = profile.lastName
      it[ProfilesTable.profile] = profile.profile
    } > 0
  }

  private suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
}