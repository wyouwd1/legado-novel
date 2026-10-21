package io.legado.app.utils.compress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * ZIP 解包的对抗性用例：所有条目只能落在目标目录内，损坏包必须显式失败。
 *
 * 用自写的最小 STORED ZIP 生成器而不是 ZipOutputStream：后者拒绝重复条目名，
 * 也无法稳定构造空条目名/反斜杠条目这类攻击输入。
 */
class ZipUtilsSecurityTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `same-prefix sibling directory cannot escape the target`() {
        val root = folder.newFolder("sibling-root")
        val target = File(root, "backup").apply { mkdirs() }
        val zip = zipWith(listOf("../backup-evil/evil.txt" to bytes("pwned")))

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, target)
        }
        assertFalse(File(root, "backup-evil/evil.txt").exists())
    }

    @Test
    fun `parent traversal is rejected`() {
        val root = folder.newFolder("parent-root")
        val target = File(root, "backup").apply { mkdirs() }
        val zip = zipWith(listOf("a/../../x.txt" to bytes("pwned")))

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, target)
        }
        assertFalse(File(root, "x.txt").exists())
    }

    @Test
    fun `absolute entry name never writes outside target`() {
        val root = folder.newFolder("absolute-root")
        val target = File(root, "backup").apply { mkdirs() }
        val outside = File("/tmp/w6c-zip-${System.nanoTime()}.txt")
        val zip = zipWith(listOf("/tmp/${outside.name}" to bytes("pwned")))

        ZipUtils.unZipToPath(zip, target)

        assertFalse(outside.exists())
        assertTrue(target.walkTopDown().all { it.canonicalPath.startsWith(target.canonicalPath) })
    }

    @Test
    fun `backslash traversal is rejected`() {
        val root = folder.newFolder("backslash-root")
        val target = File(root, "backup").apply { mkdirs() }
        val zip = zipWith(listOf("..\\backup-evil\\evil.txt" to bytes("pwned")))

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, target)
        }
        assertFalse(File(root, "backup-evil/evil.txt").exists())
    }

    @Test
    fun `blank entry name is rejected`() {
        val target = folder.newFolder("blank-root")
        val zip = zipWith(listOf("" to bytes("pwned")))

        assertThrows(SecurityException::class.java) {
            ZipUtils.unZipToPath(zip, target)
        }
    }

    @Test
    fun `directory entry creates only a directory inside target`() {
        val target = folder.newFolder("dir-root")
        val zip = zipWith(listOf("nested/目录/" to ByteArray(0)))

        val files = ZipUtils.unZipToPath(zip, target)

        assertTrue(File(target, "nested/目录").isDirectory)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `legal nested and chinese file names extract intact`() {
        val target = folder.newFolder("legal-root")
        val zip = zipWith(
            listOf(
                "目录/章节一.txt" to bytes("第一章 正文"),
                "nested/readme.txt" to bytes("hello"),
            )
        )

        val files = ZipUtils.unZipToPath(zip, target)

        assertEquals(setOf("章节一.txt", "readme.txt"), files.map { it.name }.toSet())
        assertEquals("第一章 正文", File(target, "目录/章节一.txt").readText())
        assertEquals("hello", File(target, "nested/readme.txt").readText())
    }

    @Test
    fun `duplicate names keep the last entry and stay inside target`() {
        val target = folder.newFolder("duplicate-root")
        val zip = zipWith(
            listOf(
                "dup.txt" to bytes("first"),
                "dup.txt" to bytes("second"),
            )
        )

        ZipUtils.unZipToPath(zip, target)

        assertEquals("second", File(target, "dup.txt").readText())
        assertEquals(1, target.walkTopDown().count { it.isFile })
    }

    @Test
    fun `filter excludes entries without creating them`() {
        val target = folder.newFolder("filter-root")
        val zip = zipWith(
            listOf(
                "keep.txt" to bytes("keep"),
                "drop.txt" to bytes("drop"),
            )
        )

        val files = ZipUtils.unZipToPath(zip, target) { it == "keep.txt" }

        assertEquals(listOf("keep.txt"), files.map { it.name })
        assertTrue(File(target, "keep.txt").isFile)
        assertFalse(File(target, "drop.txt").exists())
    }

    @Test
    fun `truncated zip fails explicitly`() {
        val target = folder.newFolder("truncated-root")
        val full = zipWith(listOf("a.txt" to bytes("0123456789".repeat(100))))
        val truncated = folder.newFile("truncated.zip")
        truncated.writeBytes(full.readBytes().copyOf(full.length().toInt() / 2))

        assertThrows(IOException::class.java) {
            ZipUtils.unZipToPath(truncated, target)
        }
    }

    @Test
    fun `non-zip input fails explicitly`() {
        val target = folder.newFolder("nonzip-root")
        val notZip = folder.newFile("not-a-zip.txt").apply {
            writeText("this is not a zip archive")
        }

        assertThrows(IOException::class.java) {
            ZipUtils.unZipToPath(notZip, target)
        }
    }

    @Test
    fun `empty zip extracts nothing without failing`() {
        val target = folder.newFolder("empty-root")
        val zip = zipWith(emptyList())

        assertTrue(ZipUtils.unZipToPath(zip, target).isEmpty())
    }

    private fun bytes(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    private fun zipWith(entries: List<Pair<String, ByteArray>>): File {
        val file = folder.newFile("test-${System.nanoTime()}.zip")
        val central = mutableListOf<CentralEntry>()
        RandomAccessFile(file, "rw").use { out ->
            entries.forEach { (name, data) ->
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                val crc = CRC32().apply { update(data) }.value
                val offset = out.filePointer
                out.writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
                out.writeShortLe(VERSION_NEEDED)
                out.writeShortLe(UTF8_FLAG)
                out.writeShortLe(STORED_METHOD)
                out.writeShortLe(0)
                out.writeShortLe(0)
                out.writeIntLe(crc.toInt())
                out.writeIntLe(data.size)
                out.writeIntLe(data.size)
                out.writeShortLe(nameBytes.size)
                out.writeShortLe(0)
                out.write(nameBytes)
                out.write(data)
                central += CentralEntry(nameBytes, crc, data.size, offset)
            }
            val centralStart = out.filePointer
            central.forEach { entry ->
                out.writeIntLe(CENTRAL_DIRECTORY_SIGNATURE)
                out.writeShortLe(VERSION_MADE_BY)
                out.writeShortLe(VERSION_NEEDED)
                out.writeShortLe(UTF8_FLAG)
                out.writeShortLe(STORED_METHOD)
                out.writeShortLe(0)
                out.writeShortLe(0)
                out.writeIntLe(entry.crc.toInt())
                out.writeIntLe(entry.size)
                out.writeIntLe(entry.size)
                out.writeShortLe(entry.name.size)
                out.writeShortLe(0)
                out.writeShortLe(0)
                out.writeShortLe(0)
                out.writeShortLe(0)
                out.writeIntLe(0)
                out.writeIntLe(entry.offset.toInt())
                out.write(entry.name)
            }
            val centralSize = out.filePointer - centralStart
            out.writeIntLe(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(central.size)
            out.writeShortLe(central.size)
            out.writeIntLe(centralSize.toInt())
            out.writeIntLe(centralStart.toInt())
            out.writeShortLe(0)
        }
        return file
    }

    private data class CentralEntry(
        val name: ByteArray,
        val crc: Long,
        val size: Int,
        val offset: Long,
    )

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    private companion object {
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        const val VERSION_NEEDED = 20
        const val VERSION_MADE_BY = 20
        const val UTF8_FLAG = 0x0800
        const val STORED_METHOD = 0
    }
}
