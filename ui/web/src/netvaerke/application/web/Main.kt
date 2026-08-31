package netvaerke.application.web

import io.nats.client.Nats
import io.opentelemetry.api.GlobalOpenTelemetry
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.businesslogic.network.NetworkManager
import netvaerke.manager.membership.MembershipManager

fun main(arguments: Array<String>) {
    val config = ApplicationConfig.load(arguments)

    GarageFileStorage.create(
        endpoint = config.fileStorageEndpoint,
        publicEndpoint = config.fileStoragePublicEndpoint,
        region = config.fileStorageRegion,
        accessKey = config.fileStorageAccessKey,
        secretKey = config.fileStorageSecretKey,
    ).use { fileStorage ->
        Nats.connect(config.natsUrl).use { connection ->
            Ifx {
                tracing(GlobalOpenTelemetry.get())
                service<MembershipManager> {
                    via(NatsTransport(connection, requestTimeout = config.natsRequestTimeout).requestReply(config.membershipSubject))
                }
                service<NetworkManager> {
                    via(
                        NatsTransport(connection, requestTimeout = config.networkManagerRequestTimeout)
                            .requestReply(config.networkManagerSubject),
                    )
                }
            }.use { ifx ->
                ifx.start()
                val membershipManager = ifx.create<MembershipManager>()
                val networkManager = ifx.create<NetworkManager>()
                embeddedServer(Netty, host = config.host, port = config.port) {
                    configureWebApplication(config, membershipManager, networkManager, fileStorage)
                }.start(wait = true)
            }
        }
    }
}
