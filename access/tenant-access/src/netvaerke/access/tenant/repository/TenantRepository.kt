package netvaerke.access.tenant.repository

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.Uuid
import netvaerke.access.tenant.TenantMemberRole
import netvaerke.access.tenant.TenantType

class TenantRepository(
    private val dataSource: DataSource,
) {
    fun registerTenant(tenant: TenantEntity, tenantMembers: List<TenantMemberEntity>) {
        require(tenantMembers.all { it.tenantId == tenant.id }) {
            "Tenant members must belong to the tenant being registered"
        }
        require(tenantMembers.map(TenantMemberEntity::userId).distinct().size == tenantMembers.size) {
            "A user can only have one membership in a tenant"
        }
        require(tenant.owners.distinct().size == tenant.owners.size) {
            "Tenant owners must be unique"
        }
        val memberOwners = tenantMembers
            .filter { it.role == TenantMemberRole.OWNER }
            .map(TenantMemberEntity::userId)
            .toSet()
        require(tenant.owners.toSet() == memberOwners) {
            "Tenant owners must match members with the OWNER role"
        }

        dataSource.connection.use { connection ->
            val autoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.saveTenant(tenant)
                tenantMembers.forEach { connection.saveTenantMember(it) }
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = autoCommit
            }
        }
    }

    fun getTenant(id: Uuid): TenantEntity? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_TENANT).use { statement ->
                statement.setObject(1, id.toJavaUuid())
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        result.toTenantEntity(connection.getTenantOwners(id))
                    } else {
                        null
                    }
                }
            }
        }

    fun getUserTenants(userId: Uuid): List<TenantMemberEntity> =
        dataSource.connection.use { connection ->
            connection.getTenantMembers(GET_USER_TENANTS, userId)
        }

    fun getTenantMembers(tenantId: Uuid): List<TenantMemberEntity> =
        dataSource.connection.use { connection ->
            connection.getTenantMembers(GET_TENANT_MEMBERS, tenantId)
        }

    fun addTenantMember(tenantMember: TenantMemberEntity) {
        dataSource.connection.use { connection ->
            connection.saveTenantMember(tenantMember)
        }
    }

    fun removeTenantMember(tenantMember: TenantMemberEntity) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(REMOVE_TENANT_MEMBER).use { statement ->
                statement.setObject(1, tenantMember.tenantId.toJavaUuid())
                statement.setObject(2, tenantMember.userId.toJavaUuid())
                statement.executeUpdate()
            }
        }
    }

    private fun Connection.saveTenant(tenant: TenantEntity) {
        prepareStatement(SAVE_TENANT).use { statement ->
            statement.setObject(1, tenant.id.toJavaUuid())
            statement.setString(2, tenant.type.name)
            statement.setString(3, tenant.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.saveTenantMember(tenantMember: TenantMemberEntity) {
        prepareStatement(SAVE_TENANT_MEMBER).use { statement ->
            statement.setObject(1, tenantMember.tenantId.toJavaUuid())
            statement.setObject(2, tenantMember.userId.toJavaUuid())
            statement.setString(3, tenantMember.role.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.getTenantOwners(tenantId: Uuid): List<Uuid> =
        getTenantMembers(GET_TENANT_OWNERS, tenantId).map(TenantMemberEntity::userId)

    private fun Connection.getTenantMembers(query: String, id: Uuid): List<TenantMemberEntity> =
        prepareStatement(query).use { statement ->
            statement.setObject(1, id.toJavaUuid())
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.toTenantMemberEntity())
                    }
                }
            }
        }

    private fun ResultSet.toTenantEntity(owners: List<Uuid>): TenantEntity = TenantEntity(
        id = Uuid.parse(getString("id")),
        type = TenantType.valueOf(getString("type")),
        name = getString("name"),
        owners = owners,
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun ResultSet.toTenantMemberEntity(): TenantMemberEntity = TenantMemberEntity(
        userId = Uuid.parse(getString("user_id")),
        tenantId = Uuid.parse(getString("tenant_id")),
        role = TenantMemberRole.valueOf(getString("role")),
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    private companion object {
        const val SAVE_TENANT = """
            INSERT INTO tenant.tenant (id, type, name)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                type = EXCLUDED.type,
                name = EXCLUDED.name
        """

        const val GET_TENANT = """
            SELECT id, type, name, created_at
            FROM tenant.tenant
            WHERE id = ?
        """

        const val SAVE_TENANT_MEMBER = """
            INSERT INTO tenant.tenant_member (tenant_id, user_id, role)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, user_id) DO UPDATE SET
                role = EXCLUDED.role
        """

        const val REMOVE_TENANT_MEMBER = """
            DELETE FROM tenant.tenant_member
            WHERE tenant_id = ? AND user_id = ?
        """

        const val GET_USER_TENANTS = """
            SELECT tenant_id, user_id, role
            FROM tenant.tenant_member
            WHERE user_id = ?
            ORDER BY tenant_id
        """

        const val GET_TENANT_MEMBERS = """
            SELECT tenant_id, user_id, role
            FROM tenant.tenant_member
            WHERE tenant_id = ?
            ORDER BY user_id
        """

        const val GET_TENANT_OWNERS = """
            SELECT tenant_id, user_id, role
            FROM tenant.tenant_member
            WHERE tenant_id = ? AND role = 'OWNER'
            ORDER BY user_id
        """
    }
}
