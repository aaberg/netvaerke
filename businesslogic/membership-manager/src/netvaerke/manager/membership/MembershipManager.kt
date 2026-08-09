package netvaerke.manager.membership

interface MembershipManager {
    suspend fun registerProfileWithPersonalTenant(registerProfileRequest: RegisterProfileRequest)
    suspend fun getProfile(getProfileRequest: GetProfileRequest): GetProfileResponse
}
