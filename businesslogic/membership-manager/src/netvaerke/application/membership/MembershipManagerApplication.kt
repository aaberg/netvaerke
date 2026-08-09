package netvaerke.application.membership

import io.nats.client.Connection
import javax.sql.DataSource
import netvaerke.access.profile.ProfileAccess
import netvaerke.access.profile.ProfileAccessImpl
import netvaerke.access.profile.repository.ProfileRepository
import netvaerke.access.tenant.TenantAccess
import netvaerke.access.tenant.TenantAccessImpl
import netvaerke.access.tenant.repository.TenantRepository
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx
import netvaerke.ifx.NatsTransport
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.MembershipManagerImpl

internal fun createMembershipManagerIfx(
    dataSource: DataSource,
    connection: Connection,
    config: ApplicationConfig,
): Ifx {
    val ifx = Ifx {
        service<ProfileAccess> {
            via(DirectTransport)
        }
        service<TenantAccess> {
            via(DirectTransport)
        }
        service<MembershipManager> {
            via(
                NatsTransport(
                    connection = connection,
                    requestTimeout = config.natsRequestTimeout,
                ).requestReply(
                    subject = config.membershipSubject,
                    queueGroup = config.membershipQueueGroup,
                ),
            )
        }
    }

    try {
        ifx.expose<ProfileAccess>(ProfileAccessImpl(ProfileRepository(dataSource)))
        ifx.expose<TenantAccess>(TenantAccessImpl(TenantRepository(dataSource)))
        ifx.expose<MembershipManager>(
            MembershipManagerImpl(
                profileAccess = ifx.create<ProfileAccess>(),
                tenantAccess = ifx.create<TenantAccess>(),
            ),
        )
        return ifx
    } catch (failure: Throwable) {
        ifx.close()
        throw failure
    }
}
