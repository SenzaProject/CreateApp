package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleLogParserTest {

    private fun parse(vararg lines: String): GradleLogParser {
        val parser = GradleLogParser()
        for (line in lines) parser.accept(line)
        return parser
    }

    @Test
    fun `task dihitung dan task terakhir dicatat`() {
        val p = parse(
            "> Task :app:preBuild UP-TO-DATE",
            "> Task :app:compileDebugKotlin",
            "> Task :app:assembleDebug",
        )
        assertEquals(3, p.taskCount)
        assertEquals(":app:assembleDebug", p.currentTask)
    }

    @Test
    fun `hasil akhir dan durasi dibaca`() {
        val ok = parse("BUILD SUCCESSFUL in 1m 3s")
        assertEquals(GradleOutcome.SUCCESS, ok.outcome)
        assertEquals("1m 3s", ok.duration)
        val bad = parse("BUILD FAILED in 12s")
        assertEquals(GradleOutcome.FAILED, bad.outcome)
        assertEquals("12s", bad.duration)
        assertNull(parse("sesuatu").outcome)
    }

    @Test
    fun `error Kotlin 2 dengan lokasi`() {
        val p = parse("e: file:///home/u/app/src/Main.kt:12:5 Unresolved reference 'foo'")
        assertEquals(1, p.issues.size)
        val issue = p.issues[0]
        assertEquals(IssueSeverity.ERROR, issue.severity)
        assertEquals("/home/u/app/src/Main.kt", issue.file)
        assertEquals(12, issue.line)
        assertEquals(5, issue.column)
        assertEquals("Unresolved reference 'foo'", issue.message)
    }

    @Test
    fun `error Kotlin format lama dan warning`() {
        val p = parse(
            "e: /a/B.kt: (3, 9): Type mismatch",
            "w: file:///a/C.kt:1:2 Variable 'x' is never used",
        )
        assertEquals(2, p.issues.size)
        assertEquals(3, p.issues[0].line)
        assertEquals(9, p.issues[0].column)
        assertEquals(IssueSeverity.WARNING, p.issues[1].severity)
        assertEquals(1, p.errorCount)
        assertEquals(1, p.warningCount)
    }

    @Test
    fun `error Kotlin tanpa lokasi tetap dicatat`() {
        val p = parse("e: Kotlin compile gagal")
        assertEquals(1, p.issues.size)
        assertNull(p.issues[0].file)
        assertEquals("Kotlin compile gagal", p.issues[0].message)
    }

    @Test
    fun `error Java dan AAPT dengan lokasi`() {
        val p = parse(
            "/src/Foo.java:7: error: cannot find symbol",
            "ERROR: /src/res/layout/a.xml:12: AAPT: error: attribute foo not found.",
            "/src/Bar.java:3:9: warning: deprecated",
        )
        assertEquals(3, p.issues.size)
        assertEquals("/src/Foo.java", p.issues[0].file)
        assertEquals(7, p.issues[0].line)
        assertNull(p.issues[0].column)
        assertEquals("cannot find symbol", p.issues[0].message)
        assertEquals("/src/res/layout/a.xml", p.issues[1].file)
        assertEquals("attribute foo not found.", p.issues[1].message)
        assertEquals(9, p.issues[2].column)
        assertEquals(IssueSeverity.WARNING, p.issues[2].severity)
    }

    @Test
    fun `What went wrong dibaca beserta sebabnya`() {
        val p = parse(
            "FAILURE: Build failed with an exception.",
            "",
            "* What went wrong:",
            "Execution failed for task ':app:compileDebugKotlin'.",
            "> Compilation error. See log for more details",
            "",
            "* Try:",
        )
        assertEquals(1, p.issues.size)
        assertEquals(IssueSeverity.ERROR, p.issues[0].severity)
        assertEquals(
            "Execution failed for task ':app:compileDebugKotlin'. > Compilation error. See log for more details",
            p.issues[0].message,
        )
    }

    @Test
    fun `What went wrong tanpa baris sebab`() {
        val p = parse("* What went wrong:", "A problem occurred configuring root project 'x'.", "* Try:")
        assertEquals(1, p.issues.size)
        assertEquals("A problem occurred configuring root project 'x'.", p.issues[0].message)
    }

    @Test
    fun `isu yang sama tidak ganda`() {
        val line = "e: file:///a/B.kt:1:1 Boom"
        val p = parse(line, line, line)
        assertEquals(1, p.issues.size)
    }

    @Test
    fun `jumlah isu dibatasi`() {
        val parser = GradleLogParser(maxIssues = 3)
        for (i in 1..10) parser.accept("e: file:///a/B.kt:$i:1 Boom $i")
        assertEquals(3, parser.issues.size)
        assertTrue(parser.issuesTruncated)
    }

    @Test
    fun `baris biasa tidak dianggap isu`() {
        val p = parse("Downloading https://x/y.jar", "Note: Some input files use unchecked operations.", "")
        assertTrue(p.issues.isEmpty())
        assertEquals(0, p.taskCount)
    }
}
