package io.github.onprs.universitytoolbox.feature.chaoxing.impl.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChaoxingDatabaseTest {
    private lateinit var database: ChaoxingDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChaoxingDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `account dao enforces primary ordering subaccount counting and deletion`() = runTest {
        val dao = database.accountDao()
        val primary = sampleAccount(accountId = "cx_101", isPrimary = true, createdAt = 100L)
        val sub = sampleAccount(accountId = "cx_102", isPrimary = false, createdAt = 200L)

        dao.upsert(sub)
        dao.upsert(primary)

        val ordered = dao.observeAll().first()
        assertEquals(listOf("cx_101", "cx_102"), ordered.map { it.accountId })
        assertEquals(1, dao.countSubAccounts())
        assertEquals("cx_101", dao.getPrimaryAccount()?.accountId)

        dao.markPrimary("cx_102")
        assertEquals("cx_102", dao.getPrimaryAccount()?.accountId)

        dao.deleteById("cx_101")
        assertNull(dao.getById("cx_101"))
        assertEquals(0, dao.countSubAccounts())
    }

    @Test
    fun `course and sign record daos isolate records by account`() = runTest {
        val courseDao = database.courseDao()
        val recordDao = database.signRecordDao()

        courseDao.replaceForAccount(
            "cx_101",
            listOf(
                ChaoxingCourseEntity(
                    accountId = "cx_101",
                    courseId = 10L,
                    clazzId = 20L,
                    cpi = 30L,
                    name = "高等数学",
                    className = "A班",
                    teacherName = "赵老师",
                    coverUrl = "",
                    studentCount = 50,
                    isArchived = false,
                    isPinned = true,
                    updatedAtMs = 1000L,
                ),
            ),
        )
        courseDao.replaceForAccount(
            "cx_102",
            listOf(
                ChaoxingCourseEntity(
                    accountId = "cx_102",
                    courseId = 11L,
                    clazzId = 21L,
                    cpi = 31L,
                    name = "大学物理",
                    className = "B班",
                    teacherName = "钱老师",
                    coverUrl = "",
                    studentCount = 40,
                    isArchived = false,
                    isPinned = false,
                    updatedAtMs = 1000L,
                ),
            ),
        )
        // 清空并替换 cx_101 不影响 cx_102
        courseDao.replaceForAccount("cx_101", emptyList())
        assertEquals(0, courseDao.getByAccount("cx_101").size)
        assertEquals(1, courseDao.getByAccount("cx_102").size)

        recordDao.insert(
            ChaoxingSignRecordEntity(
                accountId = "cx_101",
                maskedAccount = "138****0001",
                courseId = 10L,
                courseName = "高等数学",
                clazzId = 20L,
                activeId = 5001L,
                activityTitle = "课堂点名",
                signType = "NORMAL",
                outcome = "SUCCESS",
                message = "签到成功",
                timestampMs = 2000L,
            ),
        )
        recordDao.insert(
            ChaoxingSignRecordEntity(
                accountId = "cx_102",
                maskedAccount = "138****0002",
                courseId = 11L,
                courseName = "大学物理",
                clazzId = 21L,
                activeId = 5002L,
                activityTitle = "扫码签到",
                signType = "QR_CODE",
                outcome = "ALREADY_SIGNED",
                message = "您已签到过了",
                timestampMs = 3000L,
            ),
        )

        val recent = recordDao.observeRecent().first()
        assertEquals(listOf(5002L, 5001L), recent.map { it.activeId })
        assertEquals(1, recordDao.observeByAccount("cx_101").first().size)
    }

    @Test
    fun `preset location dao persists orders by newest first and deletes cleanly`() = runTest {
        val presetDao = database.presetLocationDao()
        val id1 = presetDao.insert(
            ChaoxingPresetLocationEntity(
                title = "第一教学楼",
                address = "海淀区双清路第一教学楼",
                latitude = 40.008123,
                longitude = 116.332456,
                createdAtMs = 1000L,
            ),
        )
        val id2 = presetDao.insert(
            ChaoxingPresetLocationEntity(
                title = "综合体育馆",
                address = "海淀区双清路综合体育馆",
                latitude = 40.010234,
                longitude = 116.335678,
                createdAtMs = 2000L,
            ),
        )

        val all = presetDao.observeAll().first()
        assertEquals(listOf(id2, id1), all.map { it.id })
        assertEquals("综合体育馆", all.first().title)

        presetDao.deleteById(id2)
        val remaining = presetDao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals("第一教学楼", remaining.single().title)
    }

    @Test
    fun `account device identity update and preference dao persist accurately`() = runTest {
        val accountDao = database.accountDao()
        val preferenceDao = database.preferenceDao()

        accountDao.upsert(sampleAccount(accountId = "cx_201", isPrimary = false, createdAt = 500L))
        accountDao.updateDeviceIdentity(
            accountId = "cx_201",
            deviceCode = "UNIFIED_DC_201",
            isUnified = true,
            uniqueId = "uid_201",
            deviceModel = "ALN-AL00",
        )
        val updated = accountDao.getById("cx_201")!!
        assertEquals("UNIFIED_DC_201", updated.deviceCode)
        assertTrue(updated.isUnifiedDeviceCode)
        assertEquals("uid_201", updated.clientUniqueId)
        assertEquals("ALN-AL00", updated.deviceModel)

        preferenceDao.upsert(ChaoxingPreferenceEntity(key = "hide_device_code_badge", value = "true"))
        assertEquals("true", preferenceDao.getValue("hide_device_code_badge"))
        assertEquals("true", preferenceDao.observeValue("hide_device_code_badge").first())
    }

    private fun sampleAccount(accountId: String, isPrimary: Boolean, createdAt: Long): ChaoxingAccountEntity =
        ChaoxingAccountEntity(
            accountId = accountId,
            uid = accountId.removePrefix("cx_"),
            fid = "100",
            maskedAccount = "138****0000",
            displayName = "测试用户",
            schoolName = "示例高校",
            avatarUrl = "",
            encryptedPasswordCiphertext = "cipher_stub",
            encryptedCookieVaultRef = accountId,
            isPrimary = isPrimary,
            isEnabled = true,
            sessionStatus = "ACTIVE",
            lastVerifiedAtMs = createdAt,
            createdAtMs = createdAt,
        )
}
