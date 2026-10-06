package com.aiassistant

import com.aiassistant.ui.components.parseLaTeXToUnicode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexAndStatsTest {

    @Test
    fun testSimpleFractions() {
        val result = parseLaTeXToUnicode("\\frac{a}{b}")
        assertEquals("a/b", result)
    }

    @Test
    fun testNestedFractions() {
        val result = parseLaTeXToUnicode("\\frac{1}{\\frac{2}{3}}")
        assertEquals("(1) / (2/3)", result)
    }

    @Test
    fun testRoots() {
        val sqrtResult = parseLaTeXToUnicode("\\sqrt{x + 1}")
        assertEquals("√(x + 1)", sqrtResult)

        val nthRoot = parseLaTeXToUnicode("\\sqrt[3]{8}")
        assertEquals("³√(8)", nthRoot)
    }

    @Test
    fun testGreekLettersAndCalculus() {
        val formula = "\\alpha + \\beta = \\gamma"
        val parsed = parseLaTeXToUnicode(formula)
        assertEquals("α + β = γ", parsed)

        val integral = "\\int_{0}^{\\infty} e^{-x} dx"
        val parsedIntegral = parseLaTeXToUnicode(integral)
        assertTrue(parsedIntegral.contains("∫"))
        assertTrue(parsedIntegral.contains("∞"))
    }

    @Test
    fun testMatrixEnvironment() {
        val matrix = "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"
        val parsed = parseLaTeXToUnicode(matrix)
        assertEquals("( a b ; c d )", parsed)
    }

    @Test
    fun testCasesEnvironment() {
        val cases = "\\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}"
        val parsed = parseLaTeXToUnicode(cases)
        assertTrue(parsed.startsWith("{"))
        assertTrue(parsed.contains("x, if x > 0"))
        assertTrue(parsed.contains("≤"))
    }

    @Test
    fun testSuperscriptsAndSubscripts() {
        val sub = "x_{12}"
        assertEquals("x₁₂", parseLaTeXToUnicode(sub))

        val sup = "x^{2}"
        assertEquals("x²", parseLaTeXToUnicode(sup))
    }

    @Test
    fun testFontCommandsStripped() {
        val textWithFonts = "\\mathbf{F} = m \\mathbf{a}"
        assertEquals("F = m a", parseLaTeXToUnicode(textWithFonts))
    }
}
