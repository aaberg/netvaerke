package netvaerke.application.web

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import netvaerke.manager.membership.GetProfileRequest
import netvaerke.manager.membership.GetProfileResponse
import netvaerke.manager.membership.MembershipManager
import netvaerke.manager.membership.RegisterProfileRequest

class WebApplicationTest {
    @Test
    fun `renders the landing page`() = testApplication {
        application {
            configureWebApplication(
                config = ApplicationConfig.fromEnvironment(
                    mapOf(
                        "NATS_URL" to "nats://localhost:4222",
                        "HANKO_API_URL" to "http://localhost:8000",
                    ),
                ),
                membershipManager = object : MembershipManager {
                    override suspend fun registerProfileWithPersonalTenant(registerProfileRequest: RegisterProfileRequest) = Unit

                    override suspend fun getProfile(getProfileRequest: GetProfileRequest): GetProfileResponse =
                        error("The landing page does not use MembershipManager")
                },
            )
        }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Make room for the people who matter."))
    }
}
