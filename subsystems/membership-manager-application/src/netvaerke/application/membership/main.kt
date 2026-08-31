package netvaerke.application.membership

import io.nats.client.Nats
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.postgresql.ds.PGSimpleDataSource

fun main(args: Array<String>) {
    val config = ApplicationConfig.load(args)
    val dataSource = PGSimpleDataSource().apply {
        setURL(config.databaseUrl)
        user = config.databaseUser
        password = config.databasePassword
    }
    dataSource.connection.use { connection ->
        check(connection.isValid(5)) { "Could not validate the database connection" }
    }

    val stopRequested = CountDownLatch(1)
    val stopped = CountDownLatch(1)
    val shutdownHook = Thread(
        {
            stopRequested.countDown()
            stopped.await(10, TimeUnit.SECONDS)
        },
        "membership-manager-shutdown",
    )
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    try {
        Nats.connect(config.natsUrl).use { connection ->
            createMembershipManagerIfx(dataSource, connection, config).use { ifx ->
                ifx.start()
                println("Membership manager listening on ${config.membershipSubject}")
                stopRequested.await()
            }
        }
    } finally {
        stopped.countDown()
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (_: IllegalStateException) {
            // JVM shutdown is already in progress.
        }
    }
}
