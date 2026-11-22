package com.sukisu.ultra.core.pkgrename

import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 换包名落盘记录的编解码。
 *
 * 只测纯函数：真实读写要 Android Context（Robolectric 不在本工程依赖里），
 * 而恢复逻辑的全部风险都集中在「两种记录不能互相误读」这一点上。
 */
class RenameJournalRecordTest {

    private val pending = RenameJournal.Pending(
        newPackage = "com.aaaa.bbbb",
        oldPackage = "com.yufeng.sukisu.ultra",
        apkPath = "/data/user/0/com.yufeng.sukisu.ultra/files/pkgrename/com.aaaa.bbbb.apk",
        certSize = 724,
        certHash = "a".repeat(64),
    )

    private val completed = RenameJournal.Completed(
        newPackage = "com.aaaa.bbbb",
        oldPackage = "com.yufeng.sukisu.ultra",
    )

    @Test
    fun 待登记记录编解码往返一致() {
        assertEquals(pending, RenameJournal.decodePending(RenameJournal.encodePending(pending)))
    }

    @Test
    fun 完成记录编解码往返一致() {
        assertEquals(completed, RenameJournal.decodeCompleted(RenameJournal.encodeCompleted(completed)))
    }

    @Test
    fun 完成记录不会被当成待登记() {
        assertNull(RenameJournal.decodePending(RenameJournal.encodeCompleted(completed)))
    }

    @Test
    fun 待登记记录不会被当成已完成() {
        assertNull(RenameJournal.decodeCompleted(RenameJournal.encodePending(pending)))
    }

    @Test
    fun 老版本没有state键的记录仍按待登记读取() {
        // 升级前的文件只有这五个键，恢复流程必须照旧能续登记。
        val legacy = Properties().apply {
            setProperty("newPackage", pending.newPackage)
            setProperty("oldPackage", pending.oldPackage)
            setProperty("apkPath", pending.apkPath)
            setProperty("certSize", pending.certSize.toString())
            setProperty("certHash", pending.certHash)
        }
        assertEquals(pending, RenameJournal.decodePending(legacy))
        assertNull(RenameJournal.decodeCompleted(legacy))
    }

    @Test
    fun 缺少新包名的完成记录被忽略() {
        val partial = Properties().apply { setProperty("state", "completed") }
        assertNull(RenameJournal.decodeCompleted(partial))
    }
}
