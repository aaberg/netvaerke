package netvaerke.manager.membership

import kotlin.uuid.Uuid

interface MembershipManager {
    fun registerProfileWithPersonalTenant(registerProfileRequest: RegisterProfileRequest)
    fun getProfile(getProfileRequest: GetProfileRequest): GetProfileResponse
}