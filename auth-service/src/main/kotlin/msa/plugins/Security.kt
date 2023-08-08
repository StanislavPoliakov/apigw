package msa.plugins

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.impl.JWTParser
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.html.*
import msa.models.Dao
import msa.models.UserCredentials
import java.security.Key
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.collections.HashMap

//private typealias SessionToken = Pair<String, SessionState>
//private typealias SessionStorage = MutableMap<String, SessionToken>
//
//private enum class SessionState {
//  ACTIVE,
//  NOT_ACTIVE
//}
//
//private val sessionStorage: SessionStorage = mutableMapOf()
//private fun SessionStorage.hasValidToken(username: String): Boolean =
//  this[username]?.let { sessionToken: SessionToken ->
//    val (token, state) = sessionToken
//    if (state == SessionState.NOT_ACTIVE) {
//      sessionStorage.remove(username)
//      false
//    } else {
//      val (_, payload, _) = token.split(".")
//      val decodedPayload = payload.decodeBase64String()
//      val expiresAt = JWTParser().parsePayload(decodedPayload).expiresAt.toInstant()
//      expiresAt.isAfter(Instant.now()).also { isAfter ->
//        if (!isAfter) sessionStorage.remove(username)
//      }
//    }
//  } ?: false


private class JWTConfig(environment: ApplicationEnvironment) {
    val issuer = environment.config.property("jwt.issuer").getString()
    val jwkProvider: JwkProvider = JwkProviderBuilder(issuer)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    val privateKeyString = environment.config.property("jwt.privateKey").getString()
        .replace(" ", "")
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()
}

private lateinit var jwtConfig: JWTConfig

private fun Application.configureRegisterEndpoint(database: Dao) {
    routing {
        post("/srv_register") {
            runCatching { call.receive<UserCredentials>() }
                .onFailure {
                    call.respond(
                        status = HttpStatusCode.BadRequest,
                        message = "User credentials not found!"
                    )
                }
                .onSuccess { credentials ->
                    runCatching { database.add(credentials) }
                        .onSuccess {
                            println("SUCCESS: $it")
                            call.respond(
                                status = HttpStatusCode.Created,
                                message = "User created!"
                            )
                        }
                        .onFailure {
                            println("FAILURE: $it")
                            call.respond(
                                status = HttpStatusCode.Conflict,
                                message = "User already exists!"
                            )
                        }
                }
        }
        delete("/srv_register") {
            call.request.queryParameters["username"]?.let { username ->
                val isDeleted = database.delete(username)

                if (isDeleted) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.Conflict)
                }
            }
        }
    }
}

private fun Application.configureLoginEndpoint(database: Dao) {
    authentication {
        form("login") {
            userParamName = "username"
            passwordParamName = "password"
            validate { credential ->
                database.getAll()
                    ?.associate { it.username to it.password }
                    ?.let { credentials ->
                        UserHashedTableAuth(
                            table = credentials,
                            digester = UserCredentials.digestFunction
                        ).authenticate(credential)
                    }
            }
            challenge {
                call.respond(
                    status = HttpStatusCode.Unauthorized,
                    message = "Not authorized!"
                )
            }
//      challenge("/login")
        }
    }

    routing {
        authenticate("login", strategy = AuthenticationStrategy.Required) {
            post("/login") {
                runCatching { call.principal<UserIdPrincipal>()!! }
                    .onSuccess { idPrincipal ->
                        val publicKey = jwtConfig.jwkProvider.get("988377D4-0B7C-42C8-BDFB-943ED19EAF1F").publicKey
                        val keySpecPKCS8 = PKCS8EncodedKeySpec(jwtConfig.privateKeyString.decodeBase64Bytes())
                        val keyFactory = KeyFactory.getInstance("RSA")
                        val privateKey = keyFactory.generatePrivate(keySpecPKCS8)

                        val token = JWT.create()
                            .withAudience(jwtConfig.audience)
                            .withIssuer(jwtConfig.issuer)
                            .withClaim("username", idPrincipal.name)
                            .withExpiresAt(Date(System.currentTimeMillis() + 120000))
                            .sign(Algorithm.RSA256(publicKey as RSAPublicKey, privateKey as RSAPrivateKey))
                        call.respond(
                            HttpStatusCode.OK, message = hashMapOf("token" to token)
                        )
                    }
            }
        }
        staticResources("/.well-known", "assets") {
            contentType { ContentType.Application.Json }
        }
        get("/login") {
            call.respondHtml {
                body {
                    h1 {
                        style = "text-align: center;"
                        +"Login"
                    }
                    form(
                        action = "/login",
                        encType = FormEncType.applicationXWwwFormUrlEncoded,
                        method = FormMethod.post
                    ) {
                        table {
                            attributes["align"] = "center"
                            attributes["border"] = "0"
                            attributes["cellpadding"] = "1"
                            attributes["cellspacing"] = "1"

                            tbody {
                                tr {
                                    td {
                                        h3 { +"username" }
                                    }
                                    td {
                                        textInput(name = "username")
                                    }
                                }
                                tr {
                                    td {
                                        h3 { +"password" }
                                    }
                                    td {
                                        passwordInput(name = "password")
                                    }
                                }
                            }
                        }
                        p {
                            style = "text-align: center;"
                            submitInput { value = "Login" }
                        }
                    }
                }
            }
        }
    }
}

fun Application.configureSecurity(database: Dao) {
    install(ContentNegotiation) {
        jackson()
    }

    jwtConfig = JWTConfig(environment)
    configureRegisterEndpoint(database)
    configureLoginEndpoint(database)

    authentication {
        jwt("token") {
            realm = jwtConfig.realm
            verifier(jwtConfig.jwkProvider, jwtConfig.issuer) {
                withAudience(jwtConfig.audience)
                withIssuer(jwtConfig.issuer)
                acceptLeeway(3)
            }
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
//  routing {
//    authenticate("token", strategy = AuthenticationStrategy.Required) {
//      get("/logout") {
//        val principal = call.principal<JWTPrincipal>()
//        val username = principal!!.payload.getClaim("username").asString()
//        sessionStorage.remove(username)
//      }
//    }
//    post("/check") {
//
//    }
//  }
}
