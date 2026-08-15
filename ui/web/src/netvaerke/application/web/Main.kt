package netvaerke.application.web

import io.nats.client.Nats
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.manager.membership.MembershipManager

fun main(arguments: Array<String>) {
    val config = ApplicationConfig.load(arguments)

    Nats.connect(config.natsUrl).use { connection ->
        Ifx {
            service<MembershipManager> {
                via(NatsTransport(connection, requestTimeout = config.natsRequestTimeout).requestReply(config.membershipSubject))
            }
        }.use { ifx ->
            ifx.start()
            val membershipManager = ifx.create<MembershipManager>()
            embeddedServer(Netty, host = config.host, port = config.port) {
                configureWebApplication(config, membershipManager)
            }.start(wait = true)
        }
    }
}
