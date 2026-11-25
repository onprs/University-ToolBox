package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 容错字符串序列化器：兼容服务端在部分接口中交替返回字符串与数字的字段。
 */
object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeString("")
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return (element as? JsonPrimitive)?.contentOrNull
        }
        return decoder.decodeString()
    }
}

/**
 * 超星宽容 JSON 解码器，忽略未知字段并容忍非标准 Content-Type 文本体。
 */
object ChaoxingJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}

@Serializable
data class FanyaLoginResponseDto(
    val status: Boolean = false,
    val url: String? = null,
    val msg2: String? = null,
    val containTwoFactorLogin: Boolean = false,
    val twoFactorLoginUrl: String? = null,
    val twoFactorLoginPCUrl: String? = null,
    val weakpwd: Boolean = false,
    @Serializable(with = FlexibleStringSerializer::class)
    val uid: String? = null,
    val passwordTimeout: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val needVcode: String? = null,
)

@Serializable
data class UserLogin4UnameResponseDto(
    val result: Int = 0,
    val errorMsg: String? = null,
    val msg: UserLogin4UnameMsgDto? = null,
)

@Serializable
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

@Serializable
data class BackClazzDataResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val status: Boolean? = null,
    val errorMsg: String? = null,
    val channelList: List<ChannelDto> = emptyList(),
)

@Serializable
data class ChannelDto(
    val id: Long = 0L,
    val cataName: String? = null,
    val key: Long = 0L,
    val cpi: Long = 0L,
    val topsign: Int = 0,
    val content: ChannelContentDto? = null,
)

@Serializable
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

@Serializable
data class CourseWrapperDto(val data: List<CourseItemDto> = emptyList())

@Serializable
data class CourseItemDto(
    val id: Long = 0L,
    val name: String? = null,
    val teacherfactor: String? = null,
    val imageurl: String? = null,
)

@Serializable
data class ActiveListResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: ActiveListDataDto? = null,
)

@Serializable
data class ActiveListDataDto(val activeList: List<ActiveItemDto> = emptyList())

/**
 * 注意：实测 `/v2/apis/active/student/activelist` 返回的 `userStatus` 恒为 1（服务端未联表查询个人考勤记录），
 * 真实个人考勤状态必须通过 `/v2/apis/sign/getAttendInfo`（[AttendInfoResponseDto]）获取。
 */
@Serializable
data class ActiveItemDto(
    val id: Long = 0L,
    val activeType: Int = 0,
    val type: Int = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val otherId: String? = null,
    val status: Int = 0,
    val nameOne: String? = null,
    val nameFour: String? = null,
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val releaseNum: Int = 0,
    val attendNum: Int = 0,
)

@Serializable
data class PptActiveInfoResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: PptActiveInfoDto? = null,
)

@Serializable
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
    @Serializable(with = FlexibleStringSerializer::class)
    val locationRange: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val latitude: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val longitude: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val locationLatitude: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val locationLongitude: String? = null,
    @SerialName("locationLatitude_gd")
    @Serializable(with = FlexibleStringSerializer::class)
    val locationLatitudeGd: String? = null,
    @SerialName("locationLongitude_gd")
    @Serializable(with = FlexibleStringSerializer::class)
    val locationLongitudeGd: String? = null,
    val starttime: Long = 0L,
    val endtime: Long? = null,
    val nowTime: Long? = null,
    val releaseNum: Int = 0,
    val attendNum: Int = 0,
)

@Serializable
data class CheckSignCodeResponseDto(val result: Int = 0, val msg: String? = null, val errorMsg: String? = null)

@Serializable
data class PanUserValidResponseDto(
    val result: Boolean = false,
    @SerialName("_token")
    val token: String? = null,
    val code: Int? = null,
    val msg: String? = null,
    val newCode: Int? = null,
)

@Serializable
data class PanUploadResponseDto(
    val result: Boolean = false,
    val objectId: String? = null,
    val data: PanUploadDataDto? = null,
    val msg: String? = null,
    val code: Int? = null,
    val newCode: Int? = null,
)

@Serializable
data class PanUploadDataDto(val objectId: String? = null, val audit: Int? = null)

@Serializable
data class SearchUnisResponseDto(val result: Boolean = false, val froms: List<UniversityItemDto> = emptyList())

@Serializable
data class UniversityItemDto(
    val schoolid: Long = 0L,
    @SerialName("_id")
    @Serializable(with = FlexibleStringSerializer::class)
    val fid: String? = null,
    val name: String = "",
)

@Serializable
data class QrAuthStatusResponseDto(
    val status: Boolean = false,
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String? = null,
    val mes: String? = null,
    val nickname: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val uid: String? = null,
)

/**
 * `/v2/apis/sign/getAttendInfo` 学生个人单次签到真实考勤详情响应。
 */
@Serializable
data class AttendInfoResponseDto(
    val result: Int = 0,
    val msg: String? = null,
    val errorMsg: String? = null,
    val data: AttendInfoDataDto? = null,
)

@Serializable
data class AttendInfoDataDto(
    val id: Long? = null,
    val activeId: Long = 0L,
    val status: Int = 0,
    val deviceCode: String? = null,
    val useragent: String? = null,
)
