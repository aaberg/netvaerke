package netvaerke.testsupport

import io.nats.client.Connection
import io.nats.client.Nats
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/** A real NATS broker shared by local integration-test runs. */
public object NatsTestBroker {
    private const val clientPort: Int = 4222

    private val container: GenericContainer<Nothing> by lazy {
        GenericContainer<Nothing>(DockerImageName.parse("nats:2.12.11-alpine")).apply {
            withExposedPorts(clientPort)
            withReuse(true)
        }
    }

    private val serverUrl: String by lazy {
        container.start()
        "nats://${container.host}:${container.getMappedPort(clientPort)}"
    }

    /** Opens an independent client connection; callers must close it after each test. */
    public fun openConnection(): Connection = Nats.connect(serverUrl)
}
