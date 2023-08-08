package msa.plugins

import io.ktor.server.application.*
import msa.models.ProfilesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.configureDatabase() {
    environment.config.propertyOrNull("database.name")?.getString().takeUnless { it.isNullOrEmpty() }?.let { dbName ->
        val databaseUrl = environment.config.property("database.url").getString()
//        val databaseUrl = "localhost"
        val databasePort = environment.config.property("database.port").getString()
//        val databasePort = "5432"
        val jdbcUrl = "jdbc:postgresql://$databaseUrl:$databasePort/$dbName"
        val driver = environment.config.property("database.driver_postgres").getString()
//        val username = Base64.getDecoder()
//            .decode(environment.config.property("database.username").getString())
//            .let(::String)
        val username = "profile_service"
//        val password = Base64.getDecoder()
//            .decode(environment.config.property("database.password").getString())
//            .let(::String)
        val password = "pass_profile_service"

        Database.connect(url = jdbcUrl, driver = driver, user = username, password = password)
    }?.also {
        transaction(it) {
            SchemaUtils.create(ProfilesTable)
        }
    }
}