package com.mothblank.signaloperator.engine

object CipherEngine {
    fun caesarShift(input: String, shift: Int): String {
        return input.map { char ->
            when {
                char.isUpperCase() -> {
                    var shifted = char + shift
                    if (shifted > 'Z') shifted -= 26
                    if (shifted < 'A') shifted += 26
                    shifted
                }
                char.isLowerCase() -> {
                    var shifted = char + shift
                    if (shifted > 'z') shifted -= 26
                    if (shifted < 'a') shifted += 26
                    shifted
                }
                else -> char
            }
        }.joinToString("")
    }

    fun reverse(input: String): String {
        return input.reversed()
    }

    fun vigenereEncrypt(input: String, keyword: String): String {
        var result = ""
        var keywordIndex = 0
        val upperKeyword = keyword.uppercase()

        for (char in input) {
            when {
                char.isUpperCase() -> {
                    val shift = upperKeyword[keywordIndex % keyword.length] - 'A'
                    var shifted = char + shift
                    if (shifted > 'Z') shifted -= 26
                    result += shifted
                    keywordIndex++
                }
                char.isLowerCase() -> {
                    val shift = upperKeyword[keywordIndex % keyword.length] - 'A'
                    var shifted = char + shift
                    if (shifted > 'z') shifted -= 26
                    result += shifted
                    keywordIndex++
                }
                else -> result += char
            }
        }
        return result
    }

    fun railFenceEncrypt(input: String): String {
        val clean = input.uppercase().filter { it.isLetter() || it == ' ' }
        val rail1 = clean.filterIndexed { index, _ -> index % 2 == 0 }
        val rail2 = clean.filterIndexed { index, _ -> index % 2 != 0 }
        return rail1 + rail2
    }

    private val natoMap = mapOf(
        'A' to "ALPHA", 'B' to "BRAVO", 'C' to "CHARLIE", 'D' to "DELTA",
        'E' to "ECHO", 'F' to "FOXTROT", 'G' to "GOLF", 'H' to "HOTEL",
        'I' to "INDIA", 'J' to "JULIETT", 'K' to "KILO", 'L' to "LIMA",
        'M' to "MIKE", 'N' to "NOVEMBER", 'O' to "OSCAR", 'P' to "PAPA",
        'Q' to "QUEBEC", 'R' to "ROMEO", 'S' to "SIERRA", 'T' to "TANGO",
        'U' to "UNIFORM", 'V' to "VICTOR", 'W' to "WHISKEY", 'X' to "XRAY",
        'Y' to "YANKEE", 'Z' to "ZULU"
    )

    fun natoEncrypt(input: String): String {
        return input.uppercase().filter { it in 'A'..'Z' }.map { char ->
            natoMap[char] ?: char.toString()
        }.joinToString(" ")
    }

    fun playfairEncrypt(input: String, key: String): String {
        val matrix = generatePlayfairMatrix(key)
        val prepared = preparePlayfairText(input)
        val result = StringBuilder()
        
        for (i in prepared.indices step 2) {
            if (i + 1 >= prepared.length) break
            val a = prepared[i]
            val b = prepared[i + 1]
            val (r1, c1) = findPosition(a, matrix)
            val (r2, c2) = findPosition(b, matrix)
            
            when {
                r1 == r2 -> {
                    result.append(matrix[r1][(c1 + 1) % 5])
                    result.append(matrix[r2][(c2 + 1) % 5])
                }
                c1 == c2 -> {
                    result.append(matrix[(r1 + 1) % 5][c1])
                    result.append(matrix[(r2 + 1) % 5][c2])
                }
                else -> {
                    result.append(matrix[r1][c2])
                    result.append(matrix[r2][c1])
                }
            }
        }
        return result.toString()
    }

    fun playfairDecrypt(input: String, key: String): String {
        val matrix = generatePlayfairMatrix(key)
        val result = StringBuilder()
        
        for (i in input.indices step 2) {
            if (i + 1 >= input.length) break
            val a = input[i]
            val b = input[i + 1]
            val (r1, c1) = findPosition(a, matrix)
            val (r2, c2) = findPosition(b, matrix)
            
            when {
                r1 == r2 -> {
                    result.append(matrix[r1][(c1 + 4) % 5])
                    result.append(matrix[r2][(c2 + 4) % 5])
                }
                c1 == c2 -> {
                    result.append(matrix[(r1 + 4) % 5][c1])
                    result.append(matrix[(r2 + 4) % 5][c2])
                }
                else -> {
                    result.append(matrix[r1][c2])
                    result.append(matrix[r2][c1])
                }
            }
        }
        return result.toString()
    }

    private fun generatePlayfairMatrix(key: String): Array<CharArray> {
        val cleanKey = (key.uppercase() + "ABCDEFGHIKLMNOPQRSTUVWXYZ")
            .map { if (it == 'J') 'I' else it }
            .filter { it in 'A'..'Z' }
            .distinct()
        
        val matrix = Array(5) { CharArray(5) }
        var index = 0
        for (r in 0 until 5) {
            for (c in 0 until 5) {
                matrix[r][c] = cleanKey[index++]
            }
        }
        return matrix
    }

    private fun preparePlayfairText(input: String): String {
        val clean = input.uppercase()
            .map { if (it == 'J') 'I' else it }
            .filter { it in 'A'..'Z' }
        
        val sb = StringBuilder()
        var i = 0
        while (i < clean.size) {
            val a = clean[i]
            sb.append(a)
            if (i + 1 < clean.size) {
                val b = clean[i + 1]
                if (a == b) {
                    sb.append('X')
                    i++
                } else {
                    sb.append(b)
                    i += 2
                }
            } else {
                sb.append('X')
                i++
            }
        }
        return sb.toString()
    }

    private fun findPosition(char: Char, matrix: Array<CharArray>): Pair<Int, Int> {
        val search = if (char == 'J') 'I' else char
        for (r in 0 until 5) {
            for (c in 0 until 5) {
                if (matrix[r][c] == search) {
                    return Pair(r, c)
                }
            }
        }
        return Pair(0, 0)
    }
}

