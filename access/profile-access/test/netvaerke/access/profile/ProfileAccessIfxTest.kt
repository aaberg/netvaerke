package netvaerke.access.profile

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import netvaerke.ifx.DirectTransport
import netvaerke.ifx.Ifx

class ProfileAccessIfxTest {
    @Test
    fun `calls profile access directly through IFX`() = runBlocking {
        Ifx {
            service<ProfileAccess> {
                via(DirectTransport)
            }
        }.use { ifx ->
            ifx.expose<ProfileAccess>(InMemoryProfileAccess())
            ifx.start()
            val access = ifx.create<ProfileAccess>()
            val profile = Profile(randomUuid(), "Ada Lovelace", "ada@example.com")

            access.registerProfile(profile)

            assertEquals(profile, access.getProfile(profile.userId))
        }
    }
}

private class InMemoryProfileAccess : ProfileAccess {
    private var profile: Profile? = null

    override suspend fun getProfile(userId: Uuid): Profile? = profile?.takeIf { it.userId == userId }

    override suspend fun registerProfile(profile: Profile) {
        this.profile = profile
    }
}

private fun randomUuid(): Uuid = Uuid.parse(UUID.randomUUID().toString())
