package netvaerke.testsupport

import javax.sql.DataSource
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** A PostgreSQL database shared by local integration-test runs. */
public object PostgresTestDatabase {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).apply {
            withReuse(true)
        }
    }

    private val dataSource: DataSource by lazy {
        container.start()
        PGSimpleDataSource().apply {
            setURL(container.jdbcUrl)
            user = container.username
            password = container.password
        }
    }

    /** Opens connections to the shared database. */
    public fun dataSource(): DataSource = dataSource
}
