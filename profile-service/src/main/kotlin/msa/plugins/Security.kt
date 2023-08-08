package msa.plugins

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.html.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import msa.models.Dao
import msa.models.UserProfile
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun Application.configureSecurity(database: Dao) {

  val issuer = environment.config.property("jwt.issuer").getString()
  val jwkProvider: JwkProvider = JwkProviderBuilder(issuer)
    .cached(10, 24, TimeUnit.HOURS)
    .rateLimited(10, 1, TimeUnit.MINUTES)
    .build()
  val audience = environment.config.property("jwt.audience").getString()
  val realm = environment.config.property("jwt.realm").getString()
  authentication {
    jwt("token") {
      this.realm = realm
      verifier(jwkProvider, issuer) {
        withAudience(audience)
        withIssuer(issuer)
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
  routing {
    authenticate("token", strategy = AuthenticationStrategy.Required) {
      get("/profile") {
        val principal = call.principal<JWTPrincipal>()
        val username = principal!!.payload.getClaim("username").asString()

        database.getProfile(username)?.let { profile ->
          val expiresAt = principal.expiresAt!!.toInstant()
          val now = Instant.now()

          val interval = (expiresAt.toEpochMilli() - now.toEpochMilli()).toDuration(DurationUnit.MILLISECONDS)

          call.respond(
            status = HttpStatusCode.OK,
            message = buildJsonObject {
              put("username", profile.username)
              put("first name", profile.firstName)
              put("last name", profile.lastName)
              put("profile", profile.profile)
              put("token expires in seconds", interval.inWholeSeconds)
            }
          )
        }
      }

      put("/profile") {
        runCatching {
          val principal = call.principal<JWTPrincipal>()
          val username = principal!!.payload.getClaim("username").asString()
          val newProfile = call.receive<UserProfile>()

          if (username != newProfile.username) {
            call.respond(HttpStatusCode.BadRequest)
          } else {
            val isUpdated = database.update(newProfile)
            if (isUpdated) {
              call.respond(
                status = HttpStatusCode.OK,
                message = "User updated!"
              )
            } else {
              call.respond(
                status = HttpStatusCode.Conflict,
                message = "User not updated!"
              )
            }
          }
        }
      }

      delete("/profile") {
        val principal = call.principal<JWTPrincipal>()
        val username = principal!!.payload.getClaim("username").asString()
        runCatching {
          val response = HttpClient(CIO).use { client ->
            client.delete("${issuer}srv_register") {
              parameter("username", username)
            }
          }

          val isCredentialsDeleted = response.status == HttpStatusCode.OK
          val isProfileDeleted = database.delete(username)

          if (isCredentialsDeleted && isProfileDeleted) {
            call.respond(
              status = HttpStatusCode.OK,
              message = "User deleted!"
            )
          } else {
            call.respond(
              status = HttpStatusCode.Conflict,
              message = """
                User not deleted!
                credentials deleted = $isCredentialsDeleted
                profile deleted = $isProfileDeleted
              """.trimIndent()
            )
          }
        }
      }
    }
    get("/register") {
      call.respondHtml {
        body {
          h1 {
            style = "text-align: center;"
            +"Register page"
          }
          form(
            action = "/register",
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
                tr {
                  td {
                    h3 { +"first name" }
                  }
                  td {
                    textInput(name = "firstname")
                  }
                }
                tr {
                  td {
                    h3 { +"last name" }
                  }
                  td {
                    textInput(name = "lastname")
                  }
                }
                tr {
                  td {
                    h3 { +"profile" }
                  }
                  td {
                    select {
                      name = "profile"
                      option(content = "product owner")
                      option(content = "team lead")
                      option(content = "developer")
                      option(content = "analyst")
                      option(content = "qa")
                      option(content = "designer")
                    }
                  }
                }
              }
            }
            p {
              style = "text-align: center;"
              submitInput { value = "Register" }
            }
          }
        }
      }
    }
    post("/register") {
      runCatching {
        val params = call.receiveParameters()
        HttpClient(CIO).use { client ->
          val response = client.post("${issuer}srv_register") {
            contentType(ContentType.Application.Json)
            setBody(
              """
                {
                  "username": "${params["username"]}",
                  "password": "${params["password"]}"
                }
              """.trimIndent()
            )
          }
          when (response.status) {
            HttpStatusCode.Created -> {
              database.add(
                UserProfile(
                  username = params["username"]!!,
                  firstName = params["firstName"]!!,
                  lastName = params["lastName"]!!,
                  profile = params["profile"]!!
                )
              )
              call.respond(
                status = response.status,
                message = "User created!"
              )
            }
            HttpStatusCode.Conflict -> {
              call.respond(
                status = response.status,
                message = "This User is already created!"
              )
            }
            else -> call.respond(response.status)
          }
        }
      }
    }
  }
}
