package com.mothblank.signaloperator.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CipherEngineTest {

    @Test
    fun testCaesarShift() {
        val input = "HELLO WORLD"
        val expected = "KHOOR ZRUOG" // Shift 3
        assertEquals(expected, CipherEngine.caesarShift(input, 3))
    }

    @Test
    fun testCaesarShiftWrapAround() {
        val input = "XYZ xyz"
        val expected = "ABC abc"
        assertEquals(expected, CipherEngine.caesarShift(input, 3))
    }

    @Test
    fun testReverse() {
        val input = "SECRET"
        val expected = "TERCES"
        assertEquals(expected, CipherEngine.reverse(input))
    }

    @Test
    fun testVigenereEncrypt() {
        val input = "ATTACK AT DAWN"
        val keyword = "LEMON"
        // A(0)+L(11)=L, T(19)+E(4)=X, T(19)+M(12)=F, A(0)+O(14)=O, C(2)+N(13)=P, K(10)+L(11)=V, space, A(0)+E(4)=E...
        // The implementation skips non-letters for keyword advancement.
        val expected = "LXFOPV EF RNHR" 
        assertEquals(expected, CipherEngine.vigenereEncrypt(input, keyword))
    }
}
