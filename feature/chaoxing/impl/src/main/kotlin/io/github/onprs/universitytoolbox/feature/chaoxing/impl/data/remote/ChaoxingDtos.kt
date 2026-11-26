package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import kotlinx.serialization.json.JsonObject

data class FanyaLoginResponseDto(
    val status: Boolean = false,
    val url: String? = null,
    val msg2: String? = null,
    val containTwoFactorLogin: Boolean = false,
    val twoFactorLoginUrl: String? = null,
    val twoFactorLoginPCUrl: String? = null,
    val weakpwd: Boolean = false,
    val uid: String? = null,
    val passwordTimeout: Int? = null,
    val needVcode: String? = null,
)

data class UserLogin4UnameResponseDto(
    val result: Int = 0,
    val errorMsg: String? = null,
    val msg: UserLogin4UnameMsgDto? = null,
)

data class UserLogin4UnameMsgDto(
    val uid: Long = 0L,
    val puid: Long = 0L,
    val fid: Int = 0,
    val name: String? = null,
    val nick: String? = null,
    val phone: String? = null,
    val uname: String? = null,
    val studentcode: String? = null,
    val schoolname: String? = null,
    val pic: String? = null,
    val status: Int = 0,
)

data class BackClazzDataResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val status: Boolean? = null,
    val errorMsg: String? = null,
    val channelList: List<ChannelDto> = emptyList(),
)

data class ChannelDto(
    val id: Long = 0L,
    val cataName: String? = null,
    val key: Long = 0L,
    val cpi: Long = 0L,
    val topsign: Int = 0,
    val content: ChannelContentDto? = null,
)

data class ChannelContentDto(
    val id: Long = 0L,
    val cpi: Long = 0L,
    val name: String? = null,
    val roletype: Int = 0,
    val state: Int = 0,
    val isFiled: Int = 0,
    val isstart: Boolean = true,
    val studentcount: Int = 0,
    val course: CourseWrapperDto? = null,
)

data class CourseWrapperDto(val data: List<CourseItemDto> = emptyList())

data class CourseItemDto(
    val id: Long = 0L,
    val name: String? = null,
    val teacherfactor: String? = null,
    val imageurl: String? = null,
)

data class ActiveListResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: ActiveListDataDto? = null,
)

data class ActiveListDataDto(val activeList: List<ActiveItemDto> = emptyList())

/**
 * 课程活动列表单条记录 DTO。
 * 实测进行中活动的 `endTime` 及未开始活动的 `startTime` 会返回空字符串 `""`，由 [ChaoxingProtocolParsers] 安全归一化。
 */
data class ActiveItemDto(
    val id: Long = 0L,
    val activeType: Int = 0,
    val type: Int = 0,
    val otherId: String? = null,
    val status: Int = 0,
    val nameOne: String? = null,
    val nameFour: String? = null,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val releaseNum: Int = 0,
    val attendNum: Int = 0,
)

data class PptActiveInfoResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: PptActiveInfoDto? = null,
)

data class PptActiveInfoDto(
    val id: Long = 0L,
    val clazzid: Long = 0L,
    val name: String? = null,
    val activeType: Int = 0,
    val otherId: Int = 0,
    val status: Int = 0,
    val source: Int = 0,
    val ifphoto: Int = 0,
    val ifopenAddress: Int = 0,
    val ifNeedVCode: Int = 0,
    val showVCode: Int = 0,
    val ifrefreshewm: Int = 0,
    val ewmRefreshTime: Int = 0,
    val openPreventCheatFlag: Int = 0,
    val openCheckFaceFlag: Int = 0,
    val openCheckWeChatFlag: Int = 0,
    val signInId: Long? = null,
    val signOutDuration: Long? = null,
    val signCode: String? = null,
    val locationText: String? = null,
    val locationRange: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val locationLatitude: String? = null,
    val locationLongitude: String? = null,
    val locationLatitudeGd: String? = null,
    val locationLongitudeGd: String? = null,
    val starttime: Long = 0L,
    val endtime: Long? = null,
    val nowTime: Long? = null,
    val releaseNum: Int = 0,
    val attendNum: Int = 0,
)

data class CheckSignCodeResponseDto(val result: Int = 0, val msg: String? = null, val errorMsg: String? = null)

data class PanUserValidResponseDto(
    val result: Boolean = false,
    val token: String? = null,
    val code: Int? = null,
    val msg: String? = null,
    val newCode: Int? = null,
)

data class PanUploadResponseDto(
    val result: Boolean = false,
    val objectId: String? = null,
    val data: PanUploadDataDto? = null,
    val msg: String? = null,
    val code: Int? = null,
    val newCode: Int? = null,
)

data class PanUploadDataDto(val objectId: String? = null, val audit: Int? = null)

data class SearchUnisResponseDto(val result: Boolean = false, val froms: List<UniversityItemDto> = emptyList())

data class UniversityItemDto(val schoolid: Long = 0L, val fid: String? = null, val name: String = "")

data class QrAuthStatusResponseDto(
    val status: Boolean = false,
    val type: String? = null,
    val mes: String? = null,
    val nickname: String? = null,
    val uid: String? = null,
)

data class AttendInfoResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: AttendInfoDataDto? = null,
)

data class AttendInfoDataDto(
    val id: Long? = null,
    val activeId: Long = 0L,
    val status: Int = 0,
    val deviceCode: String? = null,
    val useragent: String? = null,
)

/**
 * 超星全端点宽容 JSON 解析器集合。
 */
object ChaoxingProtocolParsers {
    fun parseFanyaLogin(raw: String): FanyaLoginResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        return FanyaLoginResponseDto(
            status = root.optBoolean("status") ?: false,
            url = root.optNonBlankString("url"),
            msg2 = root.optNonBlankString("msg2", "msg", "errorMsg"),
            containTwoFactorLogin = root.optBoolean("containTwoFactorLogin") ?: false,
            twoFactorLoginUrl = root.optNonBlankString("twoFactorLoginUrl"),
            twoFactorLoginPCUrl = root.optNonBlankString("twoFactorLoginPCUrl"),
            weakpwd = root.optBoolean("weakpwd") ?: false,
            uid = root.optNonBlankString("uid"),
            passwordTimeout = root.optInt("passwordTimeout"),
            needVcode = root.optNonBlankString("needVcode"),
        )
    }

    fun parseUserLogin4Uname(raw: String): UserLogin4UnameResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val msgObj = root.optObject("msg")
        val fallbackErr = root.optNonBlankString("errorMsg")
            ?: if (msgObj == null) root.optNonBlankString("msg") else null
        return UserLogin4UnameResponseDto(
            result = root.optInt("result") ?: 0,
            errorMsg = fallbackErr,
            msg = msgObj?.let(::parseUserLoginMsg),
        )
    }

    fun parseBackClazzData(raw: String): BackClazzDataResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val channels = root.optArray("channelList").mapObjectsSafely(::parseChannelItem)
        return BackClazzDataResponseDto(
            result = root.optInt("result") ?: 0,
            msg = root.optNonBlankString("msg"),
            status = root.optBoolean("status"),
            errorMsg = root.optNonBlankString("errorMsg"),
            channelList = channels,
        )
    }

    fun parseActiveList(raw: String): ActiveListResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val dataObj = root.optObject("data")
        val items = dataObj?.optArray("activeList").mapObjectsSafely(::parseActiveItem)
        return ActiveListResponseDto(
            result = root.optInt("result") ?: 0,
            msg = root.optNonBlankString("msg"),
            errorMsg = root.optNonBlankString("errorMsg"),
            data = if (dataObj != null) ActiveListDataDto(activeList = items) else null,
        )
    }

    fun parsePptActiveInfo(raw: String): PptActiveInfoResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val dataObj = root.optObject("data")
        return PptActiveInfoResponseDto(
            result = root.optInt("result") ?: 0,
            msg = root.optNonBlankString("msg"),
            errorMsg = root.optNonBlankString("errorMsg"),
            data = dataObj?.let(::parsePptActiveInfoData),
        )
    }

    fun parseAttendInfo(raw: String): AttendInfoResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val dataObj = root.optObject("data")
        val parsedData = dataObj?.let { obj ->
            AttendInfoDataDto(
                id = obj.optLong("id"),
                activeId = obj.optLong("activeId") ?: 0L,
                status = obj.optInt("status") ?: 0,
                deviceCode = obj.optNonBlankString("deviceCode"),
                useragent = obj.optNonBlankString("useragent", "userAgent"),
            )
        }
        return AttendInfoResponseDto(
            result = root.optInt("result") ?: 0,
            msg = root.optNonBlankString("msg"),
            errorMsg = root.optNonBlankString("errorMsg"),
            data = parsedData,
        )
    }

    fun parseCheckSignCode(raw: String): CheckSignCodeResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        return CheckSignCodeResponseDto(
            result = root.optInt("result") ?: 0,
            msg = root.optNonBlankString("msg"),
            errorMsg = root.optNonBlankString("errorMsg"),
        )
    }

    fun parsePanUserValid(raw: String): PanUserValidResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        return PanUserValidResponseDto(
            result = root.optBoolean("result") ?: false,
            token = root.optNonBlankString("_token", "token"),
            code = root.optInt("code"),
            msg = root.optNonBlankString("msg", "errorMsg"),
            newCode = root.optInt("newCode"),
        )
    }

    fun parsePanUpload(raw: String): PanUploadResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val dataObj = root.optObject("data")
        return PanUploadResponseDto(
            result = root.optBoolean("result") ?: false,
            objectId = root.optNonBlankString("objectId"),
            data = dataObj?.let {
                PanUploadDataDto(
                    objectId = it.optNonBlankString("objectId"),
                    audit = it.optInt("audit"),
                )
            },
            msg = root.optNonBlankString("msg", "errorMsg"),
            code = root.optInt("code"),
            newCode = root.optInt("newCode"),
        )
    }

    fun parseSearchUnis(raw: String): SearchUnisResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        val list = root.optArray("froms").mapObjectsSafely { item ->
            val name = item.optNonBlankString("name") ?: return@mapObjectsSafely null
            UniversityItemDto(
                schoolid = item.optLong("schoolid") ?: 0L,
                fid = item.optNonBlankString("_id", "fid", "schoolid"),
                name = name,
            )
        }
        return SearchUnisResponseDto(
            result = root.optBoolean("result") ?: false,
            froms = list,
        )
    }

    fun parseQrAuthStatus(raw: String): QrAuthStatusResponseDto? {
        val root = ChaoxingSafeJson.parseObjectOrNull(raw) ?: return null
        return QrAuthStatusResponseDto(
            status = root.optBoolean("status") ?: false,
            type = root.optNonBlankString("type"),
            mes = root.optNonBlankString("mes", "msg"),
            nickname = root.optNonBlankString("nickname"),
            uid = root.optNonBlankString("uid"),
        )
    }

    private fun parseUserLoginMsg(msgObj: JsonObject): UserLogin4UnameMsgDto? {
        val uid = msgObj.optLong("uid", "puid") ?: return null
        if (uid <= 0L) return null
        return UserLogin4UnameMsgDto(
            uid = uid,
            puid = msgObj.optLong("puid") ?: uid,
            fid = msgObj.optInt("fid") ?: 0,
            name = msgObj.optNonBlankString("name", "realname"),
            nick = msgObj.optNonBlankString("nick"),
            phone = msgObj.optNonBlankString("phone"),
            uname = msgObj.optNonBlankString("uname"),
            studentcode = msgObj.optNonBlankString("studentcode", "studentCode"),
            schoolname = msgObj.optNonBlankString("schoolname"),
            pic = msgObj.optNonBlankString("pic"),
            status = msgObj.optInt("status") ?: 0,
        )
    }

    private fun parseChannelItem(obj: JsonObject): ChannelDto = ChannelDto(
        id = obj.optLong("id") ?: 0L,
        cataName = obj.optNonBlankString("cataName"),
        key = obj.optLong("key") ?: 0L,
        cpi = obj.optLong("cpi") ?: 0L,
        topsign = obj.optInt("topsign") ?: 0,
        content = obj.optObject("content")?.let(::parseChannelContent),
    )

    private fun parseChannelContent(content: JsonObject): ChannelContentDto {
        val courses = content.optObject("course")?.optArray("data").mapObjectsSafely(::parseCourseItem)
        return ChannelContentDto(
            id = content.optLong("id") ?: 0L,
            cpi = content.optLong("cpi") ?: 0L,
            name = content.optNonBlankString("name"),
            roletype = content.optInt("roletype") ?: 0,
            state = content.optInt("state") ?: 0,
            isFiled = content.optInt("isFiled") ?: 0,
            isstart = content.optBoolean("isstart") ?: true,
            studentcount = content.optInt("studentcount") ?: 0,
            course = if (courses.isNotEmpty()) CourseWrapperDto(data = courses) else null,
        )
    }

    private fun parseCourseItem(c: JsonObject): CourseItemDto? {
        val cid = c.optLong("id") ?: return null
        if (cid <= 0L) return null
        return CourseItemDto(
            id = cid,
            name = c.optNonBlankString("name"),
            teacherfactor = c.optNonBlankString("teacherfactor"),
            imageurl = c.optNonBlankString("imageurl"),
        )
    }

    private fun parseActiveItem(obj: JsonObject): ActiveItemDto? {
        val id = obj.optLong("id") ?: return null
        if (id <= 0L) return null
        val resolvedActiveType = obj.optInt("activeType", "type") ?: 0
        return ActiveItemDto(
            id = id,
            activeType = resolvedActiveType,
            type = obj.optInt("type") ?: resolvedActiveType,
            otherId = obj.optNonBlankString("otherId"),
            status = obj.optInt("status") ?: 0,
            nameOne = obj.optNonBlankString("nameOne", "name"),
            nameFour = obj.optNonBlankString("nameFour"),
            startTime = obj.optLong("startTime", "starttime") ?: 0L,
            endTime = obj.optLong("endTime", "endtime"),
            releaseNum = obj.optInt("releaseNum") ?: 0,
            attendNum = obj.optInt("attendNum") ?: 0,
        )
    }

    private fun parsePptActiveInfoData(obj: JsonObject): PptActiveInfoDto? {
        val id = obj.optLong("id") ?: return null
        if (id <= 0L) return null
        val ctx = PptLookupContext(
            primary = obj,
            embedded = obj.optNonBlankString("content")?.let(ChaoxingSafeJson::parseObjectOrNull),
        )
        return PptActiveInfoDto(
            id = id,
            clazzid = ctx.longOrZero("clazzid", "clazzId"),
            name = obj.optNonBlankString("name"),
            activeType = ctx.intOrZero("activeType", "type"),
            otherId = ctx.intOrZero("otherId"),
            status = ctx.intOrZero("status"),
            source = ctx.intOrZero("source"),
            ifphoto = ctx.intOrZero("ifphoto", "ifPhoto"),
            ifopenAddress = ctx.intOrZero("ifopenAddress", "ifOpenAddress"),
            ifNeedVCode = ctx.intOrZero("ifNeedVCode", "ifneedvcode"),
            showVCode = ctx.intOrZero("showVCode", "showvcode"),
            ifrefreshewm = ctx.intOrZero("ifrefreshewm", "ifRefreshEwm"),
            ewmRefreshTime = ctx.intOrZero("ewmRefreshTime"),
            openPreventCheatFlag = ctx.intOrZero("openPreventCheatFlag"),
            openCheckFaceFlag = ctx.intOrZero("openCheckFaceFlag"),
            openCheckWeChatFlag = ctx.intOrZero("openCheckWeChatFlag"),
            signInId = ctx.longOrNull("signInId", "timerSignId"),
            signOutDuration = ctx.longOrNull("signOutDuration"),
            signCode = ctx.strOrNull("signCode"),
            locationText = ctx.strOrNull("locationText", "place"),
            locationRange = ctx.strOrNull("locationRange"),
            latitude = ctx.strOrNull("latitude"),
            longitude = ctx.strOrNull("longitude"),
            locationLatitude = ctx.strOrNull("locationLatitude"),
            locationLongitude = ctx.strOrNull("locationLongitude"),
            locationLatitudeGd = ctx.strOrNull("locationLatitude_gd", "locationLatitudeGd"),
            locationLongitudeGd = ctx.strOrNull("locationLongitude_gd", "locationLongitudeGd"),
            starttime = ctx.longOrZero("starttime", "startTime"),
            endtime = ctx.longOrNull("endtime", "endTime"),
            nowTime = obj.optLong("nowTime"),
            releaseNum = ctx.intOrZero("releaseNum"),
            attendNum = ctx.intOrZero("attendNum"),
        )
    }

    private class PptLookupContext(private val primary: JsonObject, private val embedded: JsonObject?) {
        private val timer: JsonObject? = primary.optObject("timer") ?: embedded?.optObject("timer")

        fun intOrZero(vararg keys: String): Int =
            primary.optInt(*keys) ?: embedded?.optInt(*keys) ?: timer?.optInt(*keys) ?: 0

        fun longOrNull(vararg keys: String): Long? =
            primary.optLong(*keys) ?: embedded?.optLong(*keys) ?: timer?.optLong(*keys)

        fun longOrZero(vararg keys: String): Long = longOrNull(*keys) ?: 0L

        fun strOrNull(vararg keys: String): String? =
            primary.optNonBlankString(*keys) ?: embedded?.optNonBlankString(*keys)
    }
}
