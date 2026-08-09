package netvaerke.access.profile

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import netvaerke.access.profile.repository.ProfileEntity
import netvaerke.access.profile.repository.ProfileRepository
import kotlin.uuid.Uuid

class ProfileAccessImpl(
    private val repository: ProfileRepository,
    private val jdbcDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfileAccess {
    override suspend fun getProfile(userId: Uuid): Profile? =
        withContext(jdbcDispatcher) {
            repository.getProfile(userId)
        }?.toProfile()

    override suspend fun registerProfile(profile: Profile) {
        withContext(jdbcDispatcher) {
            repository.registerProfile(profile.toEntity())
        }
    }

    private fun Profile.toEntity(): ProfileEntity = ProfileEntity(
        userId = userId,
        name = name,
        email = email,
    )

    private fun ProfileEntity.toProfile(): Profile = Profile(
        userId = userId,
        name = name,
        email = email,
    )
}
