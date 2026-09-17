package netvaerke.application.network

import io.nats.client.Connection
import io.opentelemetry.api.GlobalOpenTelemetry
import java.time.Clock
import javax.sql.DataSource
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactAccessImpl
import netvaerke.access.contact.repository.ContactRepository
import netvaerke.access.engagement.EngagementAccess
import netvaerke.access.engagement.EngagementAccessImpl
import netvaerke.access.engagement.repository.FollowUpRepository
import netvaerke.access.engagement.repository.InteractionRepository
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantAccessImpl
import netvaerke.access.tenant.repository.TenantRepository
import netvaerke.manager.network.NetworkManager
import netvaerke.manager.network.NetworkManagerImpl
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.AuthorizationEngineImpl
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport

internal fun createNetworkManagerIfx(
    dataSource: DataSource,
    connection: Connection,
    config: ApplicationConfig,
    clock: Clock = Clock.systemUTC(),
): Ifx {
    val ifx = Ifx {
        tracing(GlobalOpenTelemetry.get())
        service<ContactAccess> {
            via(DirectTransport)
        }
        service<EngagementAccess> {
            via(DirectTransport)
        }
        service<TenantAccess> {
            via(DirectTransport)
        }
        service<AuthorizationEngine> {
            via(DirectTransport)
        }
        service<NetworkManager> {
            via(
                NatsTransport(
                    connection = connection,
                    requestTimeout = config.natsRequestTimeout,
                ).requestReply(
                    subject = config.networkManagerSubject,
                    queueGroup = config.networkManagerQueueGroup,
                ),
            )
        }
    }

    try {
        ifx.expose<ContactAccess>(ContactAccessImpl(ContactRepository(dataSource)))
        ifx.expose<EngagementAccess>(
            EngagementAccessImpl(
                interactionRepository = InteractionRepository(dataSource),
                followUpRepository = FollowUpRepository(dataSource),
            ),
        )
        ifx.expose<TenantAccess>(TenantAccessImpl(TenantRepository(dataSource)))
        ifx.expose<AuthorizationEngine>(AuthorizationEngineImpl(ifx.create<TenantAccess>()))
        ifx.expose<NetworkManager>(
            NetworkManagerImpl(
                authorizer = ifx.create<AuthorizationEngine>(),
                contactAccess = ifx.create<ContactAccess>(),
                engagementAccess = ifx.create<EngagementAccess>(),
                clock = clock,
            ),
        )
        return ifx
    } catch (failure: Throwable) {
        ifx.close()
        throw failure
    }
}
