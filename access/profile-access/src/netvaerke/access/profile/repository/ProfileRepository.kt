package netvaerke.access.profile.repository

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource
import kotlin.uuid.Uuid

class ProfileRepository(
    private val dataSource: DataSource,
) {
    fun registerProfile(profile: ProfileEntity) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(REGISTER_PROFILE).use { statement ->
                statement.setObject(1, profile.userId.toJavaUuid())
                statement.setString(2, profile.name)
                statement.setString(3, profile.email)
                statement.executeUpdate()
            }
        }
    }

    fun getProfile(userId: Uuid): ProfileEntity? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_PROFILE).use { statement ->
                statement.setObject(1, userId.toJavaUuid())
                statement.executeQuery().use { result ->
                    if (result.next()) result.toProfileEntity() else null
                }
            }
        }

    private fun ResultSet.toProfileEntity(): ProfileEntity = ProfileEntity(
        userId = Uuid.parse(getString("user_id")),
        name = getString("name"),
        email = getString("email"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
    )

    private fun Uuid.toJavaUuid(): UUID = UUID.fromString(toString())

    private companion object {
        const val REGISTER_PROFILE = """
            INSERT INTO profile.profile (user_id, name, email)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email
        """

        const val GET_PROFILE = """
            SELECT user_id, name, email, created_at
            FROM profile.profile
            WHERE user_id = ?
        """
    }
}
