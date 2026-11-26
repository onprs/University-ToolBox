package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingProtocolParserTest {
    private val htmlParser = ChaoxingHtmlParser()
    private val endpointConfig = ChaoxingEndpointConfig()

    @Test
    fun `parses mooc2-ans course list html with pinned and normal courses`() {
        val sampleHtml = """
            <div id="stuTopCourseListDiv">
                <div class="course clearfix" id="c_200000001" info="100000001_100000002">
                    <div class="course-cover">
                        <a href="/mooc2-ans/mycourse/stucoursemiddle?courseid=200000001&clazzid=100000001&cpi=100000002&ismooc2=1&v=2">
                            <img src="https://p.ananas.chaoxing.com/star4/sample_hash/240_130c.jpg" />
                        </a>
                        <input class="clazzId" name="clazzId" value="100000001" />
                        <input class="courseId" name="courseId" value="200000001" />
                        <input class="curPersonId" value="100000002" />
                        <input class="role" name="role" value="0" />
                    </div>
                    <div class="course-info">
                        <span class="course-name" title="数据结构与算法">数据结构与算法</span>
                        <p class="line2 color3">张教授</p>
                        <p class="margint10 line2 color2">2026秋1班</p>
                    </div>
                </div>
            </div>
            <div id="stuNormalCourseListDiv">
                <div class="course clearfix overCourse" id="c_200000002">
                    <div class="course-cover">
                        <a href="/mooc2-ans/mycourse/stucoursemiddle?courseid=200000002&clazzid=100000003&cpi=100000004&ismooc2=1">
                            <img src="https://p.ananas.chaoxing.com/star4/sample_hash_2/240_130c.jpg" />
                        </a>
                    </div>
                    <div class="course-info">
                        <span class="course-name">计算机网络（已结课）</span>
                        <p class="line2 color3">李副教授</p>
                    </div>
                </div>
            </div>
        """.trimIndent()

        val courses = htmlParser.parseCourseListHtml(sampleHtml)
        assertEquals(2, courses.size)

        val pinned = courses[0]
        assertEquals(200000001L, pinned.courseId)
        assertEquals(100000001L, pinned.clazzId)
        assertEquals(100000002L, pinned.cpi)
        assertEquals("数据结构与算法", pinned.name)
        assertEquals("张教授", pinned.teacherName)
        assertEquals("2026秋1班", pinned.className)
        assertTrue(pinned.isPinned)
        assertFalse(pinned.isArchived)

        val fallback = courses[1]
        assertEquals(200000002L, fallback.courseId)
        assertEquals(100000003L, fallback.clazzId)
        assertEquals(100000004L, fallback.cpi)
        assertFalse(fallback.isPinned)
        assertTrue(fallback.isArchived)
    }

    @Test
    fun `parses login page hidden fields and presign metadata`() {
        val loginHtml = """
            <form>
                <input type="hidden" id="needVcode" name="needVcode" value="1" />
                <input type="hidden" id="uuid" name="uuid" value="sample-uuid-001" />
                <input type="hidden" id="enc" name="enc" value="sample-enc-token" />
            </form>
        """.trimIndent()
        val loginState = htmlParser.parseLoginPageState(loginHtml)
        assertTrue(loginState.needVcode)
        assertEquals("sample-uuid-001", loginState.uuid)
        assertEquals("sample-enc-token", loginState.enc)

        val preSignHtml = """
            <div>
                <input id="activeId" value="7000000000001" />
                <input id="activeStatus" value="1" />
                <input id="courseId" value="200000001" />
                <input id="classId" value="100000001" />
                <input id="puid" value="900001" />
                <input id="fid" value="1001" />
                <div class="zhuangtai">签到进行中</div>
            </div>
        """.trimIndent()
        val preSign = htmlParser.parsePreSignHtml(preSignHtml)
        assertEquals(7000000000001L, preSign.activeId)
        assertEquals(1, preSign.activeStatus)
        assertEquals(200000001L, preSign.courseId)
        assertEquals(100000001L, preSign.classId)
        assertEquals("签到进行中", preSign.statusHint)
    }

    @Test
    fun `decodes backclazzdata json payload safely`() {
        val courseJson = """
            {
              "result": 1,
              "msg": "获取成功",
              "channelList": [
                {
                  "cataName": "课程",
                  "key": 100000001,
                  "cpi": 100000002,
                  "topsign": 1,
                  "content": {
                    "id": 100000001,
                    "cpi": 100000002,
                    "name": "示例教学班",
                    "roletype": 3,
                    "state": 0,
                    "isFiled": 0,
                    "isstart": true,
                    "studentcount": 60,
                    "course": {
                      "data": [
                        {
                          "id": 200000001,
                          "name": "离散数学",
                          "teacherfactor": "王老师",
                          "imageurl": "http://p.ananas.chaoxing.com/star4/demo/240_130c.jpg"
                        }
                      ]
                    }
                  }
                },
                {
                  "cataName": "专题",
                  "key": 999
                }
              ]
            }
        """.trimIndent()

        val courseDto = ChaoxingProtocolParsers.parseBackClazzData(courseJson)
        assertNotNull(courseDto)
        val courses = ChaoxingDomainMapper.toCourses(courseDto!!)
        assertEquals(1, courses.size)
        assertEquals("离散数学", courses[0].name)
        assertEquals("https://p.ananas.chaoxing.com/star4/demo/240_130c.jpg", courses[0].coverUrl)
        assertTrue(courses[0].isPinned)
    }

    @Test
    fun `decodes activelist without trusting constant userStatus and maps getAttendInfo accurately`() {
        val activeJson = """
            {
              "result": 1,
              "data": {
                "activeList": [
                  {
                    "id": 7000000000001,
                    "activeType": 2,
                    "type": 2,
                    "otherId": "4",
                    "status": 2,
                    "userStatus": 1,
                    "nameOne": "第3周课堂位置签到",
                    "nameFour": "11-25 08:00",
                    "startTime": 1700000000000,
                    "endTime": 1700000600000,
                    "releaseNum": 60,
                    "attendNum": 20
                  },
                  {
                    "id": 7000000000002,
                    "activeType": 74,
                    "type": 74,
                    "otherId": 0,
                    "status": 2,
                    "userStatus": 1,
                    "nameOne": "下课签退",
                    "startTime": 1700000000000,
                    "endTime": null
                  }
                ]
              }
            }
        """.trimIndent()

        val activeDto = ChaoxingProtocolParsers.parseActiveList(activeJson)
        assertNotNull(activeDto)
        val activities = activeDto!!.data!!.activeList.map {
            ChaoxingDomainMapper.toActivity(it, courseId = 200000001L, clazzId = 100000001L)
        }
        assertEquals(ChaoxingSignType.LOCATION, activities[0].signType)
        assertEquals(ChaoxingActivityStatus.ENDED, activities[0].activityStatus)
        // activelist 返回的 userStatus=1 不得误判为已签，须等待 getAttendInfo 回填
        assertEquals(ChaoxingUserSignStatus.UNKNOWN, activities[0].userStatus)
        assertEquals(ChaoxingSignType.SIGN_OUT, activities[1].signType)
        assertTrue(activities[1].isSignOut)
        assertEquals(ChaoxingUserSignStatus.UNKNOWN, activities[1].userStatus)
    }

    @Test
    fun `maps getAttendInfo including makeup status and masks account profile`() {
        val unsignedAttend = ChaoxingProtocolParsers.parseAttendInfo(
            """{"result":1,"msg":"success","data":{"id":null,"activeId":7000000000001,"status":0}}""",
        )!!
        val absentAttend = ChaoxingProtocolParsers.parseAttendInfo(
            """{"result":1,"msg":"success","data":{"id":801,"activeId":7000000000002,"status":5}}""",
        )!!
        val signedAttend = ChaoxingProtocolParsers.parseAttendInfo(
            """
            {"result":1,"msg":"success","data":{"id":802,"activeId":7000000000003,"status":1,"deviceCode":"DC_1"}}
            """.trimIndent(),
        )!!
        val makeupAttend = ChaoxingProtocolParsers.parseAttendInfo(
            """{"result":1,"msg":"success","data":{"id":803,"activeId":7000000000004,"status":3}}""",
        )!!
        assertEquals(
            ChaoxingUserSignStatus.UNSIGNED,
            ChaoxingDomainMapper.toAttendRecord(unsignedAttend.data!!, 7000000000001L).userStatus,
        )
        assertEquals(
            ChaoxingUserSignStatus.ABSENT,
            ChaoxingDomainMapper.toAttendRecord(absentAttend.data!!, 7000000000002L).userStatus,
        )
        val signedRecord = ChaoxingDomainMapper.toAttendRecord(signedAttend.data!!, 7000000000003L)
        assertEquals(ChaoxingUserSignStatus.SIGNED, signedRecord.userStatus)
        assertEquals("DC_1", signedRecord.deviceCode)

        val makeupRecord = ChaoxingDomainMapper.toAttendRecord(makeupAttend.data!!, 7000000000004L)
        assertEquals(ChaoxingUserSignStatus.MAKEUP_SIGNED, makeupRecord.userStatus)
        assertTrue(makeupRecord.userStatus.isSignedSuccess)

        val profileDto = UserLogin4UnameMsgDto(
            uid = 123456L,
            fid = 42,
            name = "测试同学",
            phone = "13800138000",
            schoolname = "示例大学",
        )
        val profile = ChaoxingDomainMapper.toAccountProfile(profileDto, endpointConfig)
        assertEquals("138****8000", profile.maskedPhone)
        assertEquals("https://photo.chaoxing.com/p/123456_80", profile.avatarUrl)
    }

    @Test
    fun `parses qr payload formats and stuSignajax plain text responses`() {
        val urlQr = "https://mobilelearn.chaoxing.com/widget/sign/e?id=7001&c=ABCD&enc=ENC12345"
        val parsedUrl = ChaoxingQrParser.parse(urlQr)
        assertNotNull(parsedUrl)
        assertEquals(7001L, parsedUrl?.activeId)
        assertEquals("ABCD", parsedUrl?.code)
        assertEquals("ENC12345", parsedUrl?.enc)

        val rcodeQr = "https://mobilewx.chaoxing.com/widget/weixin/sign/stuSignController/preSign" +
            "?activeId=7002&rcode=SIGNIN%3Aaid%3D7002%26source%3D15%26Code%3DC99%26enc%3DXYZ789"
        val parsedRcode = ChaoxingQrParser.parse(rcodeQr)
        assertEquals(7002L, parsedRcode?.activeId)
        assertEquals("C99", parsedRcode?.code)
        assertEquals("XYZ789", parsedRcode?.enc)

        val directSignin = "SIGNIN:aid=7003&source=15&Code=K1&enc=DIRECT_ENC"
        val parsedDirect = ChaoxingQrParser.parse(directSignin)
        assertEquals(7003L, parsedDirect?.activeId)
        assertEquals("DIRECT_ENC", parsedDirect?.enc)

        assertNull(ChaoxingQrParser.parse("not-a-qr-code"))

        assertTrue(ChaoxingSignResponseParser.parse("success") is ChaoxingSignRawOutcome.Success)
        assertTrue(ChaoxingSignResponseParser.parse("您已签到过了") is ChaoxingSignRawOutcome.AlreadySigned)
        assertTrue(ChaoxingSignResponseParser.parse("签到失败，请重新扫描。") is ChaoxingSignRawOutcome.QrExpired)
        assertTrue(ChaoxingSignResponseParser.parse("签到失败[90002]") is ChaoxingSignRawOutcome.InvalidCode)
        assertTrue(
            ChaoxingSignResponseParser.parse("距教师指定签到地点230米，不在可签到范围内。")
                is ChaoxingSignRawOutcome.OutOfRange,
        )
        assertTrue(
            ChaoxingSignResponseParser.parse("photoAuthError_PCR007")
                is ChaoxingSignRawOutcome.PhotoAuthError,
        )
        assertTrue(
            ChaoxingSignResponseParser.parse("errorLocation_168.5")
                is ChaoxingSignRawOutcome.OutOfRange,
        )
    }

    @Test
    fun `maps getPPTActiveInfo gcj02 fallback to bd09 and ignores zero placeholder coordinates`() {
        val hiddenCoordJson = """
            {
              "result": 1,
              "data": {
                "id": 8001,
                "clazzid": 1001,
                "name": "隐藏坐标位置签到",
                "activeType": 2,
                "otherId": 4,
                "status": 1,
                "ifopenAddress": 1,
                "locationText": "第三教学楼",
                "locationRange": "100",
                "latitude": 0.0,
                "longitude": 0.0,
                "locationLatitude": "",
                "locationLongitude": "",
                "locationLatitude_gd": "",
                "locationLongitude_gd": ""
              }
            }
        """.trimIndent()
        val hiddenDto = ChaoxingProtocolParsers.parsePptActiveInfo(hiddenCoordJson)!!
        val hiddenDetail = ChaoxingDomainMapper.toActiveDetail(hiddenDto.data!!)
        assertFalse(hiddenDetail.hasTargetCoordinates)
        assertNull(hiddenDetail.targetLatitude)
        assertNull(hiddenDetail.targetLongitude)

        val gdOnlyJson = """
            {
              "result": 1,
              "data": {
                "id": 8002,
                "clazzid": 1001,
                "name": "高德坐标位置签到",
                "activeType": 2,
                "otherId": 4,
                "status": 1,
                "ifopenAddress": 1,
                "locationLatitude": "",
                "locationLongitude": "",
                "locationLatitude_gd": "39.908722",
                "locationLongitude_gd": "116.397499"
              }
            }
        """.trimIndent()
        val gdDto = ChaoxingProtocolParsers.parsePptActiveInfo(gdOnlyJson)!!
        val gdDetail = ChaoxingDomainMapper.toActiveDetail(gdDto.data!!)
        assertTrue(gdDetail.hasTargetCoordinates)
        assertNotNull(gdDetail.targetLatitude)
        assertNotNull(gdDetail.targetLongitude)
        assertTrue(kotlin.math.abs(gdDetail.targetLatitude!! - 39.915) < 0.01)
        assertTrue(kotlin.math.abs(gdDetail.targetLongitude!! - 116.404) < 0.01)
    }

    @Test
    fun `tolerates ongoing empty string timestamps polymorphic envelopes and isolates bad array items`() {
        val ongoingJavaJson = """
            {
              "result": 1,
              "msg": "",
              "errorMsg": null,
              "data": {
                "ext": "",
                "readingDuration": "0",
                "activeList": [
                  {
                    "id": 7000182422518,
                    "activeType": 2,
                    "type": 2,
                    "otherId": "0",
                    "status": 1,
                    "userStatus": 1,
                    "nameOne": "2026-11-26-5-6",
                    "nameFour": "",
                    "startTime": 1795672800000,
                    "endTime": "",
                    "releaseNum": null,
                    "attendNum": null
                  },
                  {
                    "id": "7000182422599",
                    "activeType": "2",
                    "otherId": "5",
                    "status": "0",
                    "nameOne": "定时签到码",
                    "startTime": "",
                    "endTime": ""
                  },
                  "unexpected_string_item",
                  {"id": 0, "nameOne": "缺失主键脏条目"},
                  {"id": "not_a_number", "nameOne": "损坏主键条目"}
                ]
              }
            }
        """.trimIndent()

        val parsed = ChaoxingProtocolParsers.parseActiveList(ongoingJavaJson)
        assertNotNull(parsed)
        val items = parsed!!.data!!.activeList
        assertEquals(2, items.size)

        val ongoing = ChaoxingDomainMapper.toActivity(items[0], courseId = 269025030L, clazzId = 159288992L)
        assertEquals(7000182422518L, ongoing.activeId)
        assertEquals(ChaoxingActivityStatus.IN_PROGRESS, ongoing.activityStatus)
        assertEquals(1795672800000L, ongoing.startTimeMs)
        assertNull(ongoing.endTimeMs)

        val unstarted = ChaoxingDomainMapper.toActivity(items[1], courseId = 269025030L, clazzId = 159288992L)
        assertEquals(7000182422599L, unstarted.activeId)
        assertEquals(ChaoxingActivityStatus.NOT_STARTED, unstarted.activityStatus)
        assertEquals(ChaoxingSignType.SIGN_CODE, unstarted.signType)
        assertEquals(0L, unstarted.startTimeMs)
        assertNull(unstarted.endTimeMs)

        // 错误态下 data 退化为 "" 或 []、msg 退化为纯文本时不得抛异常
        val emptyStringData = ChaoxingProtocolParsers.parseActiveList(
            """{"result":0,"data":"","errorMsg":"请登录后再试"}""",
        )
        assertNotNull(emptyStringData)
        assertEquals(0, emptyStringData!!.result)
        assertNull(emptyStringData.data)
        assertEquals("请登录后再试", emptyStringData.errorMsg)

        val stringMsgLogin = ChaoxingProtocolParsers.parseUserLogin4Uname(
            """{"result":0,"msg":"非法请求,请重新登录"}""",
        )
        assertNotNull(stringMsgLogin)
        assertNull(stringMsgLogin!!.msg)
        assertEquals("非法请求,请重新登录", stringMsgLogin.errorMsg)

        val forbiddenPan = ChaoxingProtocolParsers.parsePanUserValid(
            """{"code":-4,"msg":"你没有权限","newCode":200007,"result":false}""",
        )
        assertNotNull(forbiddenPan)
        assertFalse(forbiddenPan!!.result)
        assertEquals(-4, forbiddenPan.code)
    }

    @Test
    fun `parses nested content json signCode with location requirement and html or json sign responses`() {
        val codeWithLocJson = """
            {
              "result": 1,
              "data": {
                "id": 9001,
                "clazzid": 1001,
                "name": "签到码叠加位置范围校验",
                "activeType": 2,
                "otherId": "5",
                "status": 1,
                "locationRange": "150.0",
                "locationText": "信息楼B202",
                "endTime": 1795676400000,
                "content": "{\"ifopenAddress\":1,\"ifNeedVCode\":1,\"signOutDuration\":600000,\"timer\":{\"timerSignId\":8881}}"
              }
            }
        """.trimIndent()
        val dto = ChaoxingProtocolParsers.parsePptActiveInfo(codeWithLocJson)!!
        val detail = ChaoxingDomainMapper.toActiveDetail(dto.data!!)
        // otherId=5 即使开启 ifopenAddress=1 也必须保持 SIGN_CODE 类型，同时标记 requiresLocation=true
        assertEquals(ChaoxingSignType.SIGN_CODE, detail.signType)
        assertTrue(detail.requiresLocation)
        assertTrue(detail.requiresCaptcha)
        assertEquals(150, detail.locationRangeMeters)
        assertEquals(1795676400000L, detail.endTimeMs)
        assertEquals(600000L, detail.signOutDurationMs)
        assertEquals(8881L, detail.signInId)

        assertTrue(ChaoxingSignResponseParser.parse("success2") is ChaoxingSignRawOutcome.Success)
        val jsonFail = ChaoxingSignResponseParser.parse("""{"result":0,"errorMsg":"签到已结束"}""")
        assertEquals(ChaoxingSignRawOutcome.Ended("签到已结束"), jsonFail)

        val htmlStatusFail = ChaoxingSignResponseParser.parse(
            """<html><body><span id="statuscontent">抱歉，已过签到截止时间</span></body></html>""",
        )
        assertEquals(ChaoxingSignRawOutcome.Ended("抱歉，已过签到截止时间"), htmlStatusFail)

        val loginHtml = """<!DOCTYPE html><html><head><title>用户登录</title></head><body>/fanyalogin</body></html>"""
        assertTrue(ChaoxingSafeJson.looksLikeLoginHtml(loginHtml))
    }
}
