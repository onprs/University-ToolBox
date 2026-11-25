package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.remote

import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 超星登录页隐藏状态解析结果。
 */
data class ChaoxingLoginPageState(val needVcode: Boolean, val uuid: String?, val enc: String?)

/**
 * 签到预加载页面解析结果。
 */
data class ChaoxingPreSignPageInfo(
    val activeId: Long?,
    val activeStatus: Int?,
    val courseId: Long?,
    val classId: Long?,
    val puid: Long?,
    val fid: String?,
    val statusHint: String?,
)

/**
 * 超星 HTML 页面防腐解析器（覆盖 `mooc2-ans` 课程列表、登录页隐藏域及签到预加载页）。
 */
class ChaoxingHtmlParser @Inject constructor() {
    /**
     * 解析 `mooc2-ans/visit/courselistdata` 返回的课程卡片 HTML 片段。
     */
    fun parseCourseListHtml(html: String): List<ChaoxingCourse> {
        if (html.isBlank()) {
            return emptyList()
        }
        val document = Jsoup.parse(html)
        val pinnedCards = document.select("#stuTopCourseListDiv div.course.clearfix")
        val normalCards = document.select("#stuNormalCourseListDiv div.course.clearfix")
        val fallbackCards = if (pinnedCards.isEmpty() && normalCards.isEmpty()) {
            document.select("div.course.clearfix")
        } else {
            emptyList()
        }

        val parsed = ArrayList<ChaoxingCourse>()
        val seenKeys = HashSet<Pair<Long, Long>>()

        pinnedCards.forEach { card ->
            parseCourseCard(card, isPinned = true)?.let { course ->
                if (seenKeys.add(course.courseId to course.clazzId)) {
                    parsed += course
                }
            }
        }
        (normalCards + fallbackCards).forEach { card ->
            parseCourseCard(card, isPinned = false)?.let { course ->
                if (seenKeys.add(course.courseId to course.clazzId)) {
                    parsed += course
                }
            }
        }
        return parsed
    }

    /**
     * 解析 `/login` 或 `/mlogin` 页面的隐藏表单字段。
     */
    fun parseLoginPageState(html: String): ChaoxingLoginPageState {
        if (html.isBlank()) {
            return ChaoxingLoginPageState(needVcode = false, uuid = null, enc = null)
        }
        val document = Jsoup.parse(html)
        val needVcodeRaw = document.selectFirst("input#needVcode, input[name=needVcode]")
            ?.attr("value")
            ?.trim()
        val uuid = document.selectFirst("input#uuid, input[name=uuid]")
            ?.attr("value")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val enc = document.selectFirst("input#enc, input[name=enc]")
            ?.attr("value")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return ChaoxingLoginPageState(
            needVcode = needVcodeRaw == "1" || needVcodeRaw.equals("true", ignoreCase = true),
            uuid = uuid,
            enc = enc,
        )
    }

    /**
     * 解析 `preSign` 预加载页中的隐藏上下文与页面状态摘要。
     */
    fun parsePreSignHtml(html: String): ChaoxingPreSignPageInfo {
        if (html.isBlank()) {
            return ChaoxingPreSignPageInfo(
                activeId = null,
                activeStatus = null,
                courseId = null,
                classId = null,
                puid = null,
                fid = null,
                statusHint = null,
            )
        }
        val doc = Jsoup.parse(html)
        fun hiddenVal(id: String): String? =
            doc.selectFirst("input#$id, input[name=$id]")?.attr("value")?.trim()?.takeIf { it.isNotEmpty() }

        val statusHint = doc.selectFirst(".zhuangtai, .sign-status, .tip, .status-text, h2, p.title")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: when {
                doc.text().contains("签到已结束") -> "签到已结束"
                doc.text().contains("您已签到") || doc.text().contains("已签到") -> "已签到"
                else -> null
            }

        return ChaoxingPreSignPageInfo(
            activeId = hiddenVal("activeId")?.toLongOrNull(),
            activeStatus = hiddenVal("activeStatus")?.toIntOrNull(),
            courseId = hiddenVal("courseId")?.toLongOrNull(),
            classId = hiddenVal("classId")?.toLongOrNull(),
            puid = hiddenVal("puid")?.toLongOrNull(),
            fid = hiddenVal("fid"),
            statusHint = statusHint,
        )
    }

    private fun parseCourseCard(card: Element, isPinned: Boolean): ChaoxingCourse? {
        val linkParams = extractLinkParams(card)
        val infoParts = card.attr("info").split('_')
        val idAttrCourse = card.id().removePrefix("c_").toLongOrNull()

        val courseId = card.selectFirst("input.courseId[name=courseId]")?.attr("value")?.toLongOrNull()
            ?: idAttrCourse
            ?: linkParams["courseid"]?.toLongOrNull()
            ?: return null

        val clazzId = card.selectFirst("input.clazzId[name=clazzId]")?.attr("value")?.toLongOrNull()
            ?: infoParts.getOrNull(0)?.toLongOrNull()
            ?: linkParams["clazzid"]?.toLongOrNull()
            ?: return null

        val cpi = card.selectFirst("input.curPersonId")?.attr("value")?.toLongOrNull()
            ?: infoParts.getOrNull(1)?.toLongOrNull()
            ?: linkParams["cpi"]?.toLongOrNull()
            ?: 0L

        val nameElement = card.selectFirst("span.course-name")
        val courseName = nameElement?.attr("title")?.trim().orEmpty()
            .ifBlank { nameElement?.text()?.trim().orEmpty() }
        if (courseName.isBlank()) {
            return null
        }

        val roleType = card.selectFirst("input.role[name=role]")?.attr("value")?.toIntOrNull() ?: 0
        val imgElement = card.selectFirst(".course-cover img")
        val rawCoverUrl = listOf("data-original", "data-src", "src")
            .firstNotNullOfOrNull { attr -> imgElement?.attr(attr)?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
        val coverUrl = ChaoxingUrlNormalizer.normalizeImageUrl(rawCoverUrl)
        val detailLines = card.select("p.line2.color3, p.margint10.line2.color2")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        val teacherName = detailLines.firstOrNull().orEmpty()
        val className = detailLines.getOrNull(1).orEmpty()

        return ChaoxingCourse(
            courseId = courseId,
            clazzId = clazzId,
            cpi = cpi,
            name = courseName,
            className = className,
            teacherName = teacherName,
            coverUrl = coverUrl,
            studentCount = 0,
            roleType = roleType,
            isArchived = card.hasClass("overCourse") || card.selectFirst(".not-open-tip") != null,
            isStarted = true,
            isPinned = isPinned,
        )
    }

    private fun extractLinkParams(card: Element): Map<String, String> {
        val href = card.selectFirst("a[href*=stucoursemiddle]")?.attr("href")?.trim().orEmpty()
        if (href.isEmpty()) {
            return emptyMap()
        }
        val normalized = if (href.startsWith("http", ignoreCase = true)) {
            href
        } else {
            "https://mooc2-ans.chaoxing.com/${href.trimStart('/')}"
        }
        val parsed = normalized.toHttpUrlOrNull() ?: return emptyMap()
        return parsed.queryParameterNames.associateWith { key ->
            parsed.queryParameter(key).orEmpty()
        }
    }
}
