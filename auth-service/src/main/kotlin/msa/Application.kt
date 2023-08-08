package msa

import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import msa.models.DaoImpl
import msa.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(DoubleReceive)
    configureDatabase()
//    configureSerialization()
    configureSecurity(database = DaoImpl())
//    configureRouting()
}
