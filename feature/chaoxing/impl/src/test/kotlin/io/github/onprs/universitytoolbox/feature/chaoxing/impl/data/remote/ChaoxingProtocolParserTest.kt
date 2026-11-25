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

        val courseDto = ChaoxingJson.codec.decodeFromString<BackClazzDataResponseDto>(courseJson)
        val courses = ChaoxingDomainMapper.toCourses(courseDto)
        assertEquals(1, courses.size)
        assertEquals("离散数学", courses[0].name)
        assertEquals("https://p.ananas.chaoxing.com/star4/demo/240_130c.jpg", courses[0].coverUrl)
        assertTrue(courses[0].isPinned)
    }

    @Test
    fun `decodes activelist json payload and profile safely`() {
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
                    "status": 1,
                    "userStatus": 0,
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

        val activeDto = ChaoxingJson.codec.decodeFromString<ActiveListResponseDto>(activeJson)
        val activities = activeDto.data!!.activeList.map {
            ChaoxingDomainMapper.toActivity(it, courseId = 200000001L, clazzId = 100000001L)
        }
        assertEquals(ChaoxingSignType.LOCATION, activities[0].signType)
        assertEquals(ChaoxingActivityStatus.IN_PROGRESS, activities[0].activityStatus)
        assertEquals(ChaoxingUserSignStatus.UNSIGNED, activities[0].userStatus)
        assertEquals(ChaoxingSignType.SIGN_OUT, activities[1].signType)
        assertTrue(activities[1].isSignOut)
        assertEquals(ChaoxingUserSignStatus.SIGNED, activities[1].userStatus)

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
        val hiddenDto = ChaoxingJson.codec.decodeFromString<PptActiveInfoResponseDto>(hiddenCoordJson)
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
        val gdDto = ChaoxingJson.codec.decodeFromString<PptActiveInfoResponseDto>(gdOnlyJson)
        val gdDetail = ChaoxingDomainMapper.toActiveDetail(gdDto.data!!)
        assertTrue(gdDetail.hasTargetCoordinates)
        assertNotNull(gdDetail.targetLatitude)
        assertNotNull(gdDetail.targetLongitude)
        assertTrue(kotlin.math.abs(gdDetail.targetLatitude!! - 39.915) < 0.01)
        assertTrue(kotlin.math.abs(gdDetail.targetLongitude!! - 116.404) < 0.01)
    }
}
