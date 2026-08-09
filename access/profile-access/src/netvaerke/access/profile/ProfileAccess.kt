package netvaerke.access.profile

import kotlin.uuid.Uuid

public interface ProfileAccess {
    fun getProfile(userId: Uuid): Profile?
    fun registerProfile(profile: Profile)
}