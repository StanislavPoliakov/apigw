package msa.models

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import java.util.*

object CredentialsTable : IntIdTable() {
    val username = varchar("username", LENGTH_DEFAULT)
    val password = binary("password", LENGTH_DEFAULT)
}

private const val LENGTH_DEFAULT = 256