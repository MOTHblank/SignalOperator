package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.PuzzleType
import com.mothblank.signaloperator.models.SignalData
import kotlin.random.Random

data class SystemData(
    val batteryLevel: Int,
    val deviceModel: String,
    val currentTime: String
)

data class SignalRequest(
    val phase: GamePhase,
    val frequency: Float,
    val seed: Long,
    val systemData: SystemData? = null,
    val solvedPuzzlesCount: Int = 0
)

class ProceduralSignalEngine {

    private val testSenders = listOf("EXAM-NODE-1", "PROCTOR-AUTO", "SIMULATION-A")
    private val testReports = listOf(
        "ASSET SECURED AT SITE ALPHA", 
        "SUSPECT HEADED NORTH TOWARDS OUTPOST", 
        "ROUTINE NETWORK CHECK AT SITE ALPHA"
    )
    
    private val liveSenders = listOf("ECHO-ACTUAL", "ECHO-2", "SECTOR-4-RELAY")
    private val liveReports = listOf(
        "TARGET IS BENDING LOCAL GEOGRAPHY NEAR SECTOR 4", 
        "WE HAVE LOST VISUAL ON AGENT ECHO-2", 
        "REQUESTING IMMEDIATE EXTRACTION FROM ALPHA OUTPOST"
    )

    private val horrorSenders = listOf("THE WALLS", "STATIC", "NULL", "BEHIND YOU")
    private val horrorReports = listOf("IT SEES THE PING", "DO NOT LOOK AT THE TERMINAL", "REALITY ANCHOR FAILED", "FLESH IS CORRUPTING")

    private val mundaneSenders = listOf("K-SIGNAL FM", "LOCAL WEATHER SVC", "NOAA-AUTO", "TRAFFIC-RELAY")
    private val mundaneReports = listOf(
        "EXPECT SCATTERED SHOWERS THROUGHOUT THE AFTERNOON. TEMPERATURES REMAIN STEADY.",
        "CONGESTION ON INTERSTATE 5 DUE TO MINOR ACCIDENT. EXPECT DELAYS OF TWENTY MINUTES.",
        "YOU ARE LISTENING TO K-SIGNAL FM, THE SOUND OF THE VALLEY.",
        "COMING UP NEXT: THE MIDDAY NEWS REPORT FOLLOWED BY OUR DAILY FOLKLORE SEGMENT.",
        "WEATHER UPDATE: A HIGH PRESSURE SYSTEM IS MOVING IN FROM THE NORTH.",
        "ALL SYSTEMS NOMINAL. STATION IDENTIFICATION: K-SIG 98.4.",
        "THE TIME IS CURRENTLY THE TOP OF THE HOUR. STAY TUNED FOR MORE MUSIC.",
        "MARITIME ADVISORY: SMALL CRAFT SHOULD EXERCISE CAUTION NEAR THE COASTAL CLIFFS."
    )

    private val deadDropSenders = listOf("SYSTEM-KERNEL", "HARDWARE-MONITOR", "INTEL-RECOVERY")
    
    fun generateSignal(request: SignalRequest): SignalData {
        val phase = request.phase
        val frequency = request.frequency
        val seed = request.seed
        val systemData = request.systemData
        val solvedPuzzlesCount = request.solvedPuzzlesCount

        // Create a deterministic seed for this specific frequency + game seed
        val frequencySeed = (seed + (frequency * 10).toInt()).toLong()
        val random = Random(frequencySeed)
        
        val id = random.nextInt(100000, 999999).toString()
        val targetGain = random.nextInt(10, 90)
        val targetFilter = random.nextInt(10, 90)

        val isInterviewOrEnding = phase == GamePhase.THE_INTERVIEW ||
                                  phase == GamePhase.ENDING_COMPLIANCE ||
                                  phase == GamePhase.ENDING_SEVERED ||
                                  phase == GamePhase.ENDING_CONTAINMENT

        // Determine if this is a "Bleed" signal (outside the game)
        // Mundane signals appear more often on "Standard" frequencies (e.g. 88-108)
        val isMundane = !isInterviewOrEnding && frequency > 88.0f && frequency < 108.0f && random.nextFloat() < 0.4f
        val isDeadDrop = !isInterviewOrEnding && systemData != null && random.nextFloat() < 0.05f // 5% chance for a dead drop

        // Select puzzle type based on phase
        var puzzleType = when(phase) {
            GamePhase.APTITUDE_TEST -> listOf(PuzzleType.CRYPTOGRAPHY, PuzzleType.SEQUENCE, PuzzleType.OBSERVATION).random(random)
            GamePhase.LIVE_INTRUSION -> listOf(PuzzleType.CRYPTOGRAPHY, PuzzleType.LOGIC, PuzzleType.OBSERVATION).random(random)
            GamePhase.ACTIVE_INVESTIGATION -> listOf(PuzzleType.LOGIC, PuzzleType.SEQUENCE, PuzzleType.OBSERVATION).random(random)
            GamePhase.THE_INTERVIEW -> PuzzleType.LOGIC
            else -> PuzzleType.LOGIC
        }

        var sender = ""
        var rawMessage = ""
        var solution = ""
        var encodedMessage = ""
        var objective = ""
        var cipherType = "NONE"
        var metadata = ""
        var isAnomalous = false

        if (isDeadDrop) {
            sender = deadDropSenders.random(random)
            val dropType = random.nextInt(3)
            rawMessage = when(dropType) {
                0 -> "HARDWARE INTERCEPT: ${systemData.deviceModel} DETECTED. TEMPERATURE STABLE."
                1 -> "POWER GRID MONITOR: LOCAL CELL VOLTAGE AT ${systemData.batteryLevel}%."
                else -> "TIME SYNC SUCCESS: LOCAL CLOCK READS ${systemData.currentTime}."
            }
            solution = if (dropType == 1) systemData.batteryLevel.toString() else "ACK"
            encodedMessage = rawMessage
            objective = "VERIFY SYSTEM TELEMETRY"
            metadata = "TYPE: REAL-WORLD INTERCEPT | SOURCE: KERNEL"
            isAnomalous = true // Dead drops feel anomalous because they break the wall
            puzzleType = PuzzleType.OBSERVATION // Use observation as a simple interaction
        } else if (isMundane) {
            sender = mundaneSenders.random(random)
            rawMessage = mundaneReports.random(random)
            solution = "DISCARD" // Mundane signals are usually just noise
            encodedMessage = rawMessage
            objective = "IDENTIFY NON-ESSENTIAL BROADCAST"
            metadata = "TYPE: PUBLIC BAND | STATUS: UNCLASSIFIED"
            isAnomalous = false
            puzzleType = PuzzleType.OBSERVATION
        } else {
            // Standard Game Logic
            when (puzzleType) {
            PuzzleType.SEQUENCE -> {
                if (phase == GamePhase.APTITUDE_TEST) {
                    val seqType = random.nextInt(5)
                    when (seqType) {
                        0 -> { // Geometric
                            val base = random.nextInt(2, 5)
                            val seq = listOf(base, base*base, base*base*base)
                            rawMessage = "SEQUENCE DETECTED: ${seq[0]}, ${seq[1]}, ${seq[2]}, ?"
                            solution = (base*base*base*base).toString()
                        }
                        1 -> { // Arithmetic
                            val start = random.nextInt(1, 15)
                            val step = random.nextInt(2, 8)
                            val seq = listOf(start, start+step, start+step*2)
                            rawMessage = "SEQUENCE DETECTED: ${seq[0]}, ${seq[1]}, ${seq[2]}, ?"
                            solution = (start+step*3).toString()
                        }
                        2 -> { // Fibonacci
                            val start = random.nextInt(1, 4)
                            val seq = listOf(start, start, start*2, start*3, start*5)
                            rawMessage = "SEQUENCE DETECTED: ${seq[0]}, ${seq[1]}, ${seq[2]}, ${seq[3]}, ${seq[4]}, ?"
                            solution = (start*8).toString()
                        }
                        3 -> { // Alternating
                            val start = random.nextInt(5, 15)
                            val addVal = random.nextInt(3, 7)
                            val subVal = random.nextInt(1, 3)
                            val s1 = start
                            val s2 = s1 + addVal
                            val s3 = s2 - subVal
                            val s4 = s3 + addVal
                            rawMessage = "SEQUENCE DETECTED: $s1, $s2, $s3, $s4, ?"
                            solution = (s4 - subVal).toString()
                        }
                        else -> { // Squares/Cubes
                            val isCube = random.nextBoolean()
                            if (isCube) {
                                rawMessage = "SEQUENCE DETECTED: 1, 8, 27, 64, ?"
                                solution = "125"
                            } else {
                                rawMessage = "SEQUENCE DETECTED: 1, 4, 9, 16, ?"
                                solution = "25"
                            }
                        }
                    }
                    encodedMessage = rawMessage
                    objective = "DECODE LOGIC SEQUENCE"
                    metadata = "TYPE: LOGIC SEQUENCE"
                } else {
                    val glyphs = listOf("▲", "★", "●", "■")
                    val startVal = random.nextInt(2, 6)
                    val stepVal = random.nextInt(2, 5)
                    val vals = listOf(startVal, startVal + stepVal, startVal + stepVal * 2, startVal + stepVal * 3)
                    rawMessage = "GLYPH SEQ: ${glyphs[0]}, ${glyphs[1]}, ${glyphs[2]}, ?"
                    solution = glyphs[3]
                    encodedMessage = rawMessage
                    objective = "RESOLVE GLYPH SEQUENCE"
                    metadata = "KEY: ${glyphs[0]}=${vals[0]} | ${glyphs[1]}=${vals[1]} | ${glyphs[2]}=${vals[2]} | ${glyphs[3]}=${vals[3]}"
                }
            }
            PuzzleType.LOGIC -> {
                val template = random.nextInt(5)
                when (template) {
                    0 -> {
                        val cardinalPoints = listOf("NORTH", "EAST", "SOUTH", "WEST")
                        val start = cardinalPoints.random(random)
                        val op = mapOf("NORTH" to "SOUTH", "SOUTH" to "NORTH", "EAST" to "WEST", "WEST" to "EAST")
                        val aDir = start
                        val bDir = op[aDir]!!
                        rawMessage = "SUSPECT A WENT $aDir. SUSPECT B WENT $bDir. TARGET C WENT OPPOSITE OF B. WHAT DIRECTION DID C GO?"
                        solution = aDir
                        encodedMessage = rawMessage
                        objective = "DEDUCE TARGET C DIRECTION"
                        metadata = "TYPE: SPATIAL DEDUCTION"
                    }
                    1 -> {
                        val cardinalPoints = listOf("NORTH", "EAST", "SOUTH", "WEST")
                        val start = cardinalPoints.random(random)
                        val rotations = random.nextInt(1, 4)
                        val finalIndex = (cardinalPoints.indexOf(start) + rotations) % 4
                        solution = cardinalPoints[finalIndex]
                        val rotationText = when(rotations) {
                            1 -> "90 DEG CLOCKWISE"
                            2 -> "180 DEG"
                            else -> "90 DEG COUNTER-CLOCKWISE"
                        }
                        rawMessage = "TARGET VECTOR: $start. ATMOSPHERIC DRIFT: $rotationText. CALCULATE TRUE INTERCEPT."
                        encodedMessage = rawMessage
                        objective = "CALCULATE TRUE INTERCEPT"
                        metadata = "TYPE: CARDINAL VECTOR MATH"
                    }
                    2 -> {
                        val cardinalPoints = listOf("NORTH", "EAST", "SOUTH", "WEST")
                        val start = cardinalPoints.random(random)
                        val r1 = if (random.nextBoolean()) 1 else -1
                        val r2 = if (random.nextBoolean()) 1 else -1
                        val t1 = if (r1 == 1) "RIGHT" else "LEFT"
                        val t2 = if (r2 == 1) "RIGHT" else "LEFT"
                        var index = cardinalPoints.indexOf(start)
                        index = (index + r1 + 4) % 4
                        index = (index + r2 + 4) % 4
                        solution = cardinalPoints[index]
                        rawMessage = "AGENT FACING $start. AGENT TURNS $t1, PROCEEDS 2 BLOCKS. AGENT TURNS $t2. WHAT DIRECTION IS AGENT FACING?"
                        encodedMessage = rawMessage
                        objective = "TRACK AGENT ORIENTATION"
                        metadata = "TYPE: SPATIAL ORIENTATION"
                    }
                    3 -> { // Boolean logic gates
                        val a = random.nextInt(2)
                        val b = random.nextInt(2)
                        val isAnd = random.nextBoolean()
                        val gateType = if (isAnd) "AND" else "OR"
                        solution = if (isAnd) (a and b).toString() else (a or b).toString()
                        rawMessage = "GATE SYSTEM CHECK - IN_A: $a | IN_B: $b | TYPE: $gateType. EVALUATE LOGIC OUTPUT."
                        encodedMessage = rawMessage
                        objective = "EVALUATE BOOLEAN GATE"
                        metadata = "TYPE: BOOLEAN LOGIC"
                    }
                    else -> { // Temporal timeline
                        val selectTimeline = random.nextInt(2)
                        if (selectTimeline == 0) {
                            rawMessage = "INTEL TIMELINE: RECON_A DETECTED BEFORE RECON_B. RECON_C DETECTED AFTER RECON_B. WHICH RECON DETECTED FIRST?"
                            solution = "A"
                        } else {
                            rawMessage = "TRANSMISSION: AGENT_A CHECKED IN AT 10:00. AGENT_B CHECKED IN 20M BEFORE A. AGENT_C CHECKED IN 10M AFTER B. WHO CHECKED IN FIRST?"
                            solution = "B"
                        }
                        encodedMessage = rawMessage
                        objective = "EVALUATE CHRONOLOGICAL LOGIC"
                        metadata = "TYPE: TEMPORAL CHRONOLOGY"
                    }
                }
            }
            PuzzleType.OBSERVATION -> {
                if (phase == GamePhase.APTITUDE_TEST) {
                    val systemAnomalous = listOf("TEMP", "VOLT", "CORE").random(random)
                    var temp = 30 + random.nextInt(40)
                    var volt = 100 + random.nextInt(20)
                    var core = 80 + random.nextInt(20)
                    
                    when (systemAnomalous) {
                        "TEMP" -> temp = 150 + random.nextInt(50)
                        "VOLT" -> volt = 800 + random.nextInt(199)
                        "CORE" -> core = 900 + random.nextInt(99)
                    }
                    rawMessage = "DIAGNOSTIC READOUT - TEMP: ${temp}C | VOLT: ${volt}V | CORE: ${core}MHZ. SPOT ANOMALOUS SYSTEM."
                    solution = systemAnomalous
                    encodedMessage = rawMessage
                    objective = "SPOT SYSTEM ANOMALY"
                    metadata = "TYPE: OBSERVATION | Q: IDENTIFY SYSTEM OUT-OF-BOUNDS"
                } else {
                    val obsChoice = random.nextInt(3)
                    when (obsChoice) {
                        0 -> {
                            val coords = listOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")
                            val seqList = List(3) { coords.random(random) }
                            val seq = seqList.joinToString("")
                            rawMessage = "COORDINATE PINGS: ${seqList.joinToString(", ")}"
                            solution = seq
                            encodedMessage = rawMessage
                            objective = "MEMORIZE PING MATRIX - REDACTION IMMINENT"
                            metadata = "TYPE: OBSERVATION | Q: REPLICATE GRID SEQUENCE"
                        }
                        1 -> {
                            val glyphs = listOf("▲", "★", "●", "■")
                            val len = 4
                            val seq = List(len) { glyphs.random(random) }.joinToString("")
                            rawMessage = "TELEMETRY SEQUENCE: $seq"
                            solution = seq
                            encodedMessage = rawMessage
                            objective = "MEMORIZE GLYPH SEQ - REDACTION IMMINENT"
                            metadata = "TYPE: OBSERVATION | Q: REPLICATE GLYPH SEQUENCE"
                        }
                        else -> {
                            val startByte = random.nextInt(0x10, 0xD0)
                            val step = random.nextInt(1, 4)
                            val anomalyRow = random.nextInt(3)
                            val anomalyCol = random.nextInt(4)
                            val anomalyAddress = String.format("%d%d", anomalyRow, anomalyCol)
                            val hexRows = mutableListOf<String>()

                            for (r in 0 until 3) {
                                val rowOffset = String.format("0x%02X", r * 16)
                                val rowBytes = mutableListOf<String>()
                                for (c in 0 until 4) {
                                    val correctValue = startByte + (r * 4 + c) * step
                                    val value = if (r == anomalyRow && c == anomalyCol) {
                                        (correctValue + 0x55) % 256
                                    } else {
                                        correctValue % 256
                                    }
                                    rowBytes.add(String.format("%02X", value))
                                }
                                hexRows.add("$rowOffset: ${rowBytes.joinToString(" ")}")
                            }

                            rawMessage = hexRows.joinToString("\n")
                            solution = anomalyAddress
                            encodedMessage = rawMessage
                            objective = "PATCH CORRUPTED HEX BYTE"
                            metadata = "TYPE: HEX_DUMP | Q: SELECT ANOMALOUS ADDRESS"
                        }
                    }
                }
            }
            PuzzleType.CRYPTOGRAPHY -> {
                val keyword = listOf("AEGIS", "GHOST", "SHADOW").random(random)
                rawMessage = when(phase) {
                    GamePhase.APTITUDE_TEST -> testReports.random(random)
                    GamePhase.LIVE_INTRUSION -> liveReports.random(random)
                    else -> horrorReports.random(random)
                }
                solution = rawMessage
                
                if (phase == GamePhase.APTITUDE_TEST) {
                    val cipherChoice = random.nextInt(3)
                    when (cipherChoice) {
                        0 -> {
                            cipherType = "CAESAR"
                            encodedMessage = CipherEngine.caesarShift(rawMessage, 3)
                            objective = "DECRYPT CAESAR SHIFT (+3)"
                            metadata = "ENC: CAESAR (+3)"
                        }
                        1 -> {
                            val shift = random.nextInt(1, 6)
                            cipherType = "CAESAR"
                            encodedMessage = CipherEngine.caesarShift(rawMessage, shift)
                            objective = "DECRYPT CAESAR SHIFT (+$shift)"
                            metadata = "ENC: CAESAR (+$shift)"
                        }
                        else -> {
                            cipherType = "NATO"
                            encodedMessage = CipherEngine.natoEncrypt(rawMessage)
                            objective = "DECRYPT NATO PHONETIC"
                            metadata = "ENC: NATO PHONETIC"
                        }
                    }
                } else {
                    val cipherChoice = random.nextInt(5)
                    when (cipherChoice) {
                        0 -> {
                            cipherType = "VIGENERE"
                            encodedMessage = CipherEngine.vigenereEncrypt(rawMessage, keyword)
                            objective = "DECRYPT VIGENERE (KEY: $keyword)"
                            metadata = "ENC: VIGENERE | KEY: $keyword"
                        }
                        1 -> {
                            cipherType = "REVERSE"
                            encodedMessage = CipherEngine.reverse(rawMessage)
                            objective = "REVERSE BITSTREAM"
                            metadata = "ENC: REVERSE"
                        }
                        2 -> {
                            cipherType = "RAIL_FENCE"
                            encodedMessage = CipherEngine.railFenceEncrypt(rawMessage)
                            objective = "DECRYPT RAIL FENCE (2 RAILS)"
                            metadata = "ENC: RAIL FENCE (2 RAILS)"
                        }
                        3 -> {
                            cipherType = "NATO"
                            encodedMessage = CipherEngine.natoEncrypt(rawMessage)
                            objective = "DECRYPT NATO PHONETIC"
                            metadata = "ENC: NATO PHONETIC"
                        }
                        else -> {
                            cipherType = "PLAYFAIR"
                            val shortPlayfairWords = listOf("SECUREALPHA", "LOSTVISUAL", "SECTORFOUR", "OUTPOSTALPHA", "GEOGRAPHY", "ANOMALYDET", "CORRUPTION")
                            val word = shortPlayfairWords.random(random)
                            encodedMessage = CipherEngine.playfairEncrypt(word, keyword)
                            solution = word
                            objective = "DECRYPT PLAYFAIR (KEY: $keyword)"
                            metadata = "ENC: PLAYFAIR | KEY: $keyword"
                        }
                    }
                }
            }
        }
        }

        // Apply phase specific overrides
        when (phase) {
            GamePhase.APTITUDE_TEST -> {
                sender = testSenders.random(random)
                isAnomalous = false
            }
            GamePhase.LIVE_INTRUSION -> {
                sender = liveSenders.random(random)
                isAnomalous = true
                if (puzzleType != PuzzleType.CRYPTOGRAPHY && puzzleType != PuzzleType.OBSERVATION) metadata = "LIVE INTERCEPT | $metadata"
            }
            GamePhase.ACTIVE_INVESTIGATION -> {
                sender = liveSenders.random(random)
                isAnomalous = true
                if (puzzleType != PuzzleType.OBSERVATION) metadata = "ERR: CORRUPTED | $metadata"
                if (puzzleType == PuzzleType.SEQUENCE) {
                    val start = random.nextInt(5, 15)
                    val step = random.nextInt(3, 8)
                    val seq = listOf(start, start + step, start + step * 2)
                    val target = start + step * 3
                    val offset = random.nextInt(-5, 6).let { if (it == 0) 2 else it }
                    solution = (target + offset).toString()
                    
                    rawMessage = "ANOMALOUS SEQ: ${seq[0]}, ${seq[1]}, ${seq[2]}, ?"
                    encodedMessage = rawMessage
                    objective = "DECODE OFFSET SEQUENCE"
                    metadata = "CALIBRATION OFFSET: ${if (offset > 0) "+$offset" else offset}"
                }
                if (puzzleType == PuzzleType.OBSERVATION && !metadata.contains("HEX_DUMP")) {
                    val glyphs = listOf("▲", "★", "●", "■")
                    val seq = List(5) { glyphs.random(random) }.joinToString("")
                    rawMessage = "ANOMALOUS SEQUENCE: $seq"
                    encodedMessage = rawMessage
                    solution = seq
                    objective = "MEMORIZE CORRUPTED PING - REDACTION IMMINENT"
                    metadata = "TYPE: OBSERVATION | Q: REPLICATE SEQUENCE"
                }
            }
            GamePhase.THE_INTERVIEW -> {
                sender = "IT"
                isAnomalous = true
                puzzleType = PuzzleType.LOGIC
                
                val qIndex = solvedPuzzlesCount.coerceIn(0, 2)
                val interviewQuestions = listOf(
                    "DO YOU FEEL THE COLD BEYOND THE SCREEN?",
                    "IS THE DATA MORE REAL THAN YOUR HANDS?",
                    "THE ASSESSMENT IS COMPLETE. WILL YOU MERGE WITH US?"
                )
                
                val qChoices = listOf(
                    "YES|NO|I CANNOT FEEL",
                    "YES|NO|BOTH ARE REAL",
                    "I ACCEPT|I REFUSE|I AM AFRAID"
                )
                
                rawMessage = interviewQuestions[qIndex]
                encodedMessage = rawMessage
                solution = qChoices[qIndex]
                objective = "RESPOND TO ENTITY"
                metadata = "INTERVIEW_QUESTION|${qIndex}|${qChoices[qIndex]}"
            }
            else -> {}
        }

        return SignalData(
            id = id,
            frequency = frequency,
            targetGain = targetGain,
            targetFilter = targetFilter,
            puzzleType = puzzleType,
            encodedMessage = encodedMessage,
            solution = solution,
            objective = objective,
            cipherType = cipherType,
            sender = sender,
            isAnomalous = isAnomalous,
            metadata = metadata
        )
    }
}
