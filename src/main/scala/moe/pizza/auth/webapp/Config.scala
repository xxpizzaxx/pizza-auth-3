package moe.pizza.auth.webapp

case class Config(
    login_url: String = "https://login.eveonline.com/",
    crest_url: String = "https://crest-tq.eveonline.com/",
    clientID: String,
    secretKey: String,
    redirectUrl: String
)
