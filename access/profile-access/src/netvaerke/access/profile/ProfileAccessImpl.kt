package netvaerke.access.profile

import netvaerke.access.profile.repository.ProfileEntity
import netvaerke.access.profile.repository.ProfileRepository
import kotlin.uuid.Uuid

class ProfileAccessImpl(
    private val repository: ProfileRepository,
) : ProfileAccess {
    override fun getProfile(userId: Uuid): Profile? =
        repository.getProfile(userId)?.toProfile()

    override fun registerProfile(profile: Profile) {
        repository.registerProfile(profile.toEntity())
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
