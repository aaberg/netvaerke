package netvaerke.access.profile

import kotlin.uuid.Uuid

interface ProfileAccess {
    fun getProfile(userId: Uuid): Profile?
    fun registerProfile(profile: Profile)
}