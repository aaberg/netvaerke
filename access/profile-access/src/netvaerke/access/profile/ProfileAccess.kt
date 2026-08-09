package netvaerke.access.profile

import kotlin.uuid.Uuid

public interface ProfileAccess {
    suspend fun getProfile(userId: Uuid): Profile?
    suspend fun registerProfile(profile: Profile)
}
