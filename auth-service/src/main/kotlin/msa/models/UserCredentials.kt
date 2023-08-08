package msa.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
//
//@OptIn(ExperimentalSerializationApi::class)
//@Serializer(UserCredentials::class)
//object UserCredentialsSerializer : KSerializer<UserCredentials> {
//  override val descriptor: SerialDescriptor = buildClassSerialDescriptor(serialName = "UserCredentials") {
//    element<String>("username")
//    element<String>("password")
//  }
//
//
//}

//class UserCredentialsDeserializer : JsonDeserializer<UserCredentials>() {
//  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UserCredentials {
//    val codec = p.codec
//    val node: JsonNode = codec.readTree(p)
//    val username = node["username"].asText()
//    val password = node["password"].asText()
//
//    return UserCredentials(username)
//  }
//
//}

@JsonDeserialize
class UserCredentials {
  val username: String
  val password: ByteArray

  constructor(username: String, password: ByteArray) {
    this.username = username
    this.password = password
  }

  @JsonCreator constructor(
    @JsonProperty("username") username: String,
    @JsonProperty("password") password: String
  ) : this(username, UserCredentials.digestFunction(password))

  val id: Int
    get() = username.hashCode()

  override fun toString(): String {
    return """
      username = $username
      password = ${String(password)}
    """.trimIndent()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as UserCredentials

    return username == other.username
  }

  override fun hashCode(): Int {
    return username.hashCode()
  }

  companion object {
    val digestFunction = getDigestFunction("SHA-256") { "auth_${it.length}" }
  }
}
