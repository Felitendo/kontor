package de.felitendo.kontor.data.transfer

import de.felitendo.kontor.data.store.KontorJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QrTransferTest {
    /** Big enough for several frames even after compression. */
    private val json = buildString {
        append("{\"app\":\"kontor\",\"items\":[")
        val r = Random(7)
        repeat(400) { i -> if (i > 0) append(','); append("{\"id\":\"").append(r.nextLong().toString(36)).append("\",\"v\":").append(r.nextDouble()).append('}') }
        append("]}")
    }

    @Test
    fun framesArriveInAnyOrderWithRepeats() {
        val frames = QrTransfer.frames(json)
        assertTrue(frames.size > 2)
        val a = QrTransfer.Assembler()
        val shuffled = (frames + frames.take(2)).shuffled(Random(1))
        shuffled.forEach { a.offer(it) }
        assertTrue(a.complete)
        assertEquals(json, a.result())
    }

    @Test
    fun repeatedFrameIsNotNew() {
        val frames = QrTransfer.frames(json)
        val a = QrTransfer.Assembler()
        assertTrue(a.offer(frames[0]))
        assertFalse(a.offer(frames[0]))
        assertFalse(a.complete)
    }

    @Test
    fun foreignCodesAreIgnored() {
        val a = QrTransfer.Assembler()
        assertFalse(a.offer("https://example.com"))
        assertFalse(a.offer("KTR1/abcd/3/ff/9/xyz"))
        assertEquals(0, a.count)
    }

    @Test
    fun restartedSenderStartsOver() {
        val old = QrTransfer.frames(json)
        val new = QrTransfer.frames(json)
        val a = QrTransfer.Assembler()
        a.offer(old[0]); a.offer(old[1])
        new.forEach { a.offer(it) }
        assertTrue(a.complete)
        assertEquals(json, a.result())
    }

    @Test(expected = IllegalStateException::class)
    fun damagedDataFailsTheChecksum() {
        val frames = QrTransfer.frames(json).toMutableList()
        val p = frames[1].split('/', limit = 6)
        val payload = p[5]
        val flipped = (if (payload[3] == 'A') 'B' else 'A').toString()
        frames[1] = p.take(5).joinToString("/") + "/" + payload.replaceRange(3, 4, flipped)
        val a = QrTransfer.Assembler()
        frames.forEach { a.offer(it) }
        a.result()
    }

    @Test
    fun backupFrom10StillReads() {
        val old = """{"app":"kontor","schema":1,"exportedAt":1700000000000,"assets":[]}"""
        val backup = KontorJson.decodeFromString(Backup.serializer(), old)
        assertEquals(1, backup.schema)
        assertEquals(null, backup.settings)
    }

    @Test
    fun backupSurvivesTheTrip() {
        val backup = Backup(exportedAt = 1_700_000_000_000, assets = emptyList(), settings = PortableSettings(hideAmounts = true, lockTimeout = 60))
        val encoded = KontorJson.encodeToString(Backup.serializer(), backup)
        val a = QrTransfer.Assembler()
        QrTransfer.frames(encoded).forEach { a.offer(it) }
        assertEquals(backup, KontorJson.decodeFromString(Backup.serializer(), a.result()))
    }
}
