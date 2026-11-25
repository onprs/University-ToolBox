package io.github.onprs.universitytoolbox.feature.chaoxing.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 学习通工具全局唯一标识，用于 ToolRegistry 注册、授权范围校验及远程开关治理。
 */
const val CHAOXING_TOOL_ID: String = "chaoxing_assistant"

/**
 * 学习通功能模块 Navigation 3 路由键集合。
 */
@Serializable
sealed interface ChaoxingNavKey : NavKey {
    /** 学习通主入口：课程列表、当前账号概览与快速监控面板。 */
    @Serializable
    data object Dashboard : ChaoxingNavKey

    /** 多账号管理与登录绑定页面（密码登录、二维码扫码与配额状态）。 */
    @Serializable
    data object Accounts : ChaoxingNavKey

    /** 指定课程的活动列表、签到监控与执行入口。 */
    @Serializable
    data class CourseActivities(
        val accountId: String,
        val courseId: Long,
        val clazzId: Long,
        val cpi: Long,
        val courseName: String,
    ) : ChaoxingNavKey

    /** 单个签到活动的详情核查、策略参数录入与批量执行页面。 */
    @Serializable
    data class SignExecution(val accountId: String, val courseId: Long, val clazzId: Long, val activeId: Long) :
        ChaoxingNavKey

    /** 本地签到审计记录与协议诊断页面。 */
    @Serializable
    data class SignRecords(val accountId: String? = null) : ChaoxingNavKey
}
