package netvaerke.application.network

import io.nats.client.Connection
import javax.sql.DataSource
import netvaerke.access.contact.ContactAccess
import netvaerke.access.contact.ContactAccessImpl
import netvaerke.access.contact.repository.ContactRepository
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantAccessImpl
import netvaerke.access.tenant.repository.TenantRepository
import netvaerke.businesslogic.network.NetworkManager
import netvaerke.businesslogic.network.NetworkManagerImpl
import netvaerke.engine.authorization.AuthorizationEngine
import netvaerke.engine.authorization.AuthorizationEngineImpl
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport

internal fun createNetworkManagerIfx(
    dataSource: DataSource,
    connection: Connection,
    config: ApplicationConfig,
): Ifx {
    val ifx = Ifx {
        service<ContactAccess> {
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
        ifx.expose<TenantAccess>(TenantAccessImpl(TenantRepository(dataSource)))
        ifx.expose<AuthorizationEngine>(AuthorizationEngineImpl(ifx.create<TenantAccess>()))
        ifx.expose<NetworkManager>(
            NetworkManagerImpl(
                authorizer = ifx.create<AuthorizationEngine>(),
                contactAccess = ifx.create<ContactAccess>(),
            ),
        )
        return ifx
    } catch (failure: Throwable) {
        ifx.close()
        throw failure
    }
}
