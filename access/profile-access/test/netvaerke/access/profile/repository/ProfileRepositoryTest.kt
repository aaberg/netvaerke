package netvaerke.access.profile.repository

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import netvaerke.access.profile.Profile
import netvaerke.access.profile.ProfileAccess
import netvaerke.access.profile.ProfileAccessImpl
import netvaerke.testsupport.PostgresTestDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ProfileRepositoryTest {
    private val dataSource: DataSource
        get() = ProfileTestDatabase.dataSource

    private val access: ProfileAccess
        get() = ProfileAccessImpl(ProfileRepository(dataSource))

    @BeforeTest
    fun clearProfiles() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("TRUNCATE TABLE profile.profile")
            }
        }
    }

    @Test
    fun `registers and retrieves a profile`() {
        val profile = Profile(
            userId = randomUuid(),
            name = "Ada Lovelace",
            email = "ada@example.com",
        )

        access.registerProfile(profile)

        assertEquals(profile, access.getProfile(profile.userId))
    }

    @Test
    fun `updates a registered profile and returns null for an unknown user`() {
        val userId = randomUuid()
        val profile = Profile(
            userId = userId,
            name = "Grace Hopper",
            email = "grace@example.com",
        )
        access.registerProfile(profile)

        val updated = profile.copy(name = "Rear Admiral Grace Hopper")
        access.registerProfile(updated)

        assertEquals(updated, access.getProfile(userId))
        assertNull(access.getProfile(randomUuid()))
    }
}

private object ProfileTestDatabase {
    val dataSource: DataSource by lazy {
        PostgresTestDatabase.dataSource().also(::runMigrations)
    }

    private fun runMigrations(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase(
                "changelog-root.yaml",
                DirectoryResourceAccessor(liquibaseDirectory()),
                database,
            ).use { liquibase ->
                liquibase.update(Contexts(), LabelExpression())
            }
        }
    }

    private fun liquibaseDirectory(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("liquibase") }
            .firstOrNull { Files.isRegularFile(it.resolve("changelog-root.yaml")) }
            ?: error("Could not locate the Liquibase changelog directory")
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
