package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

/**
 * 超星学习通官方服务端点配置，支持在测试中替换为 MockWebServer 地址。
 */
data class ChaoxingEndpointConfig(
    val passportBaseUrl: String = OFFICIAL_PASSPORT_BASE,
    val ssoBaseUrl: String = OFFICIAL_SSO_BASE,
    val mooc1ApiBaseUrl: String = OFFICIAL_MOOC1_API_BASE,
    val mooc2AnsBaseUrl: String = OFFICIAL_MOOC2_ANS_BASE,
    val mobilelearnBaseUrl: String = OFFICIAL_MOBILELEARN_BASE,
    val panYzBaseUrl: String = OFFICIAL_PAN_YZ_BASE,
    val captchaBaseUrl: String = OFFICIAL_CAPTCHA_BASE,
    val photoBaseUrl: String = OFFICIAL_PHOTO_BASE,
) {
    val mloginPageUrl: String get() = "$passportBaseUrl/mlogin"
    val loginPageUrl: String get() = "$passportBaseUrl/login"
    val fanyaLoginUrl: String get() = "$passportBaseUrl/fanyalogin"
    val fanyaLoginByCodeUrl: String get() = "$passportBaseUrl/fanyaloginbycode"
    val unitLoginUrl: String get() = "$passportBaseUrl/unitlogin"
    val createQrUrl: String get() = "$passportBaseUrl/createqr"
    val refreshQrUrl: String get() = "$passportBaseUrl/refreshQRCode"
    val getAuthStatusV2Url: String get() = "$passportBaseUrl/getauthstatus/v2"
    val getAccountMessageUrl: String get() = "$passportBaseUrl/mooc/getAccountMessage"
    val searchUnisUrl: String get() = "$passportBaseUrl/org/searchUnis"
    val orgGetNameUrl: String get() = "$passportBaseUrl/org/getName"

    val userLogin4UnameUrl: String get() = "$ssoBaseUrl/apis/login/userLogin4Uname.do"
    val backClazzDataUrl: String get() = "$mooc1ApiBaseUrl/mycourse/backclazzdata"
    val visitInteractionUrl: String get() = "$mooc2AnsBaseUrl/visit/interaction"
    val courseListDataUrl: String get() = "$mooc2AnsBaseUrl/mooc2-ans/visit/courselistdata"

    val activeListUrl: String get() = "$mobilelearnBaseUrl/v2/apis/active/student/activelist"
    val pcActiveListUrl: String get() = "$mobilelearnBaseUrl/widget/pcpick/stu/activeList"
    val getPptActiveInfoUrl: String get() = "$mobilelearnBaseUrl/v2/apis/active/getPPTActiveInfo"
    val newSignPreSignUrl: String get() = "$mobilelearnBaseUrl/newsign/preSign"
    val pcSignPreSignUrl: String get() = "$mobilelearnBaseUrl/widget/sign/pcStuSignController/preSign"
    val stuSignAjaxUrl: String get() = "$mobilelearnBaseUrl/pptSign/stuSignajax"
    val checkSignCodeUrl: String get() = "$mobilelearnBaseUrl/widget/sign/pcStuSignController/checkSignCode"
    val signInV2Url: String get() = "$mobilelearnBaseUrl/v2/apis/sign/signIn"
    val qrSignRedirectUrl: String get() = "$mobilelearnBaseUrl/widget/sign/e"

    val panUserValidUrl: String get() = "$panYzBaseUrl/api/token/uservalid"
    val panUploadUrl: String get() = "$panYzBaseUrl/upload"
    val captchaConfUrl: String get() = "$captchaBaseUrl/captcha/get/conf"

    /** 生成用户头像地址。 */
    fun avatarUrl(uid: String, size: Int = DEFAULT_AVATAR_SIZE): String = "$photoBaseUrl/p/${uid}_$size"

    companion object {
        const val OFFICIAL_PASSPORT_BASE = "https://passport2.chaoxing.com"
        const val OFFICIAL_SSO_BASE = "https://sso.chaoxing.com"
        const val OFFICIAL_MOOC1_API_BASE = "https://mooc1-api.chaoxing.com"
        const val OFFICIAL_MOOC2_ANS_BASE = "https://mooc2-ans.chaoxing.com"
        const val OFFICIAL_MOBILELEARN_BASE = "https://mobilelearn.chaoxing.com"
        const val OFFICIAL_PAN_YZ_BASE = "https://pan-yz.chaoxing.com"
        const val OFFICIAL_CAPTCHA_BASE = "https://captcha.chaoxing.com"
        const val OFFICIAL_PHOTO_BASE = "https://photo.chaoxing.com"

        const val DEFAULT_CAPTCHA_ID = "GcXX5vewqE7DezKGlyvleKCnkTglvGpL"
        const val DEFAULT_REFER = "http%3A%2F%2Fi.mooc.chaoxing.com"
        const val DEFAULT_APP_TYPE = "15"
        const val DEFAULT_FID = "-1"
        const val DEFAULT_AVATAR_SIZE = 80

        const val MOBILE_WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        val EQUIVALENT_COOKIE_DOMAINS: List<String> = listOf(
            ".chaoxing.com",
            ".cxwfw.com",
            ".xuexitong.com",
        )
    }
}
