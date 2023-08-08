package msa.models

import io.ktor.server.html.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

interface Dao {
    suspend fun add(userCredentials: UserCredentials): Int

    suspend fun getCredentialsBy(username: String): UserCredentials?

    suspend fun delete(username: String): Boolean

    suspend fun changePassword(userCredentials: UserCredentials): Boolean

    suspend fun getAll(): List<UserCredentials>?
}

class DaoImpl : Dao {
    private fun resultRowToUserEntity(row: ResultRow): UserCredentials = UserCredentials(
        username = row[CredentialsTable.username],
        password = row[CredentialsTable.password]
    )
    override suspend fun add(userCredentials: UserCredentials): Int = dbQuery {
        CredentialsTable.insertAndGetId {
            it[id] = userCredentials.id
            it[username] = userCredentials.username
            it[password] = userCredentials.password
        }.value
    }

    override suspend fun getCredentialsBy(username: String): UserCredentials? = dbQuery {
        CredentialsTable
            .select { CredentialsTable.username eq username }
            .map(::resultRowToUserEntity)
            .singleOrNull()
    }

    override suspend fun getAll(): List<UserCredentials>? = dbQuery {
        CredentialsTable
            .selectAll()
            .map(::resultRowToUserEntity)
            .takeUnless { it.isEmpty() }
    }

    override suspend fun delete(username: String): Boolean = dbQuery {
        CredentialsTable.deleteWhere { CredentialsTable.username eq username } > 0
    }

    override suspend fun changePassword(userCredentials: UserCredentials): Boolean = dbQuery {
        CredentialsTable.update({ CredentialsTable.username eq userCredentials.username }) {
            it[password] = userCredentials.password
        } > 0
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}