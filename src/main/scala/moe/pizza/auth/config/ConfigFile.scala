package moe.pizza.auth.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Created by andi on 19/02/16.
  */
object ConfigFile {
  case class EmbeddedLdapConfig(
                                 instancePath: String = "./ldap",
                                 port: Long = 389,
                                 basedn: String = "ou=pizza",
                                 host: String = "localhost"
                               )
  case class AuthGroupConfig(closed: List[String], open: List[String], public: List[String])
  case class AuthConfig(
                        mode: String,
                        corporation: Option[String],
                        alliance: Option[String],
                        groups: AuthGroupConfig
                       )
  case class CrestConfig(
                        @JsonProperty("login_url")
                        loginUrl: String,
                        @JsonProperty("crest_url")
                        crestUrl: String,
                        clientID: String,
                        secretKey: String,
                        redirectUrl: String
                        )
  case class ConfigFile(
                         crest: CrestConfig,
                         auth: AuthConfig,
                         embeddedldap: EmbeddedLdapConfig
                       )

}
