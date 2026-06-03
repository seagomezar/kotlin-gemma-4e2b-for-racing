package com.example.chatbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanableTelemetryDecoderTest {
    @Test
    fun decode450_extractsEdge38EcuBlock1() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x450, u16(6900), u16(3), u16(1234), u16(1855))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(6900.0, packet?.rpm ?: 0.0, 0.001)
        assertEquals(3, packet?.gear)
        assertEquals(123.4, packet?.speed ?: 0.0, 0.001)
        assertEquals(185.5, packet?.waterTempF ?: 0.0, 0.001)
    }

    @Test
    fun decode451_extractsEdge38WheelSpeeds() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x451, u16(1001), u16(999), u16(1003), u16(997))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(100.1, packet?.wheelSpeedFlMph ?: 0.0, 0.001)
        assertEquals(99.9, packet?.wheelSpeedFrMph ?: 0.0, 0.001)
        assertEquals(100.3, packet?.wheelSpeedRlMph ?: 0.0, 0.001)
        assertEquals(99.7, packet?.wheelSpeedRrMph ?: 0.0, 0.001)
        assertEquals(listOf(0.1, -0.1, 0.3, -0.3), packet?.wheelSpeedDeltas?.map { "%.1f".format(it).toDouble() })
    }

    @Test
    fun decode452_extractsEdge38EcuOilTempAndPedal() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x452, u16(2150), u16(0), u16(0), u16(5342))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(215.0, packet?.engineOilTempF ?: 0.0, 0.001)
        assertEquals(53.42, packet?.ecuDbwApp1Percent ?: 0.0, 0.001)
        assertEquals(53.42, packet?.throttle ?: 0.0, 0.001)
        assertEquals(53.42, packet?.pedalPositionPercent ?: 0.0, 0.001)
    }

    @Test
    fun decode453_extractsEdge38FuelAndBrakeSwitch() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x453, u16(1350), u16(0), u16(0), u16(1))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(13.5, packet?.fuelLevelGallons ?: 0.0, 0.001)
        assertTrue(packet?.brakeSwitchApplied == true)
    }

    @Test
    fun decode454_extractsEdge38MilOutput() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x454, u16(1), u16(0), u16(0), u16(0))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(1, packet?.ecuMilOut)
    }

    @Test
    fun decode455_recoversEdge38SignedAccelerometers() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x455, s16(-123), s16(55), s16(-98), u16(0))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(-1.23, packet?.inlineAccelG ?: 0.0, 0.001)
        assertEquals(0.55, packet?.lateralAccelG ?: 0.0, 0.001)
        assertEquals(-0.98, packet?.verticalAccelG ?: 0.0, 0.001)
    }

    @Test
    fun decode456_recoversEdge38SignedGyroscopes() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x456, s16(-321), s16(-40), s16(456), u16(0))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(-32.1, packet?.rollRateDps ?: 0.0, 0.001)
        assertEquals(-4.0, packet?.pitchRateDps ?: 0.0, 0.001)
        assertEquals(45.6, packet?.yawRateDps ?: 0.0, 0.001)
    }

    @Test
    fun decode457_extractsEdge38PressuresAndGpsSpeed() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x457, u16(877), u16(1234), u16(421), u16(725))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(87.7, packet?.oilPressurePsi ?: 0.0, 0.001)
        assertEquals(123.4, packet?.speed ?: 0.0, 0.001)
        assertEquals(42.1, packet?.fuelPressurePsi ?: 0.0, 0.001)
        assertEquals(725.0, packet?.brakePressurePsi ?: 0.0, 0.001)
        assertEquals(725.0, packet?.brake ?: 0.0, 0.001)
    }

    @Test
    fun decode458_extractsEdge38AnalogOilTemp() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x458, u16(2405), u16(0), u16(0), u16(0))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(240.5, packet?.analogOilTempF ?: 0.0, 0.001)
        assertEquals(240.5, packet?.oilFilterTempF ?: 0.0, 0.001)
    }

    @Test
    fun decode459_recoversEdge38SignedDeg7Coordinates() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x459, s32(371234567), s32(-1219876543))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertEquals(37.1234567, packet?.latitude ?: 0.0, 0.0000001)
        assertEquals(-121.9876543, packet?.longitude ?: 0.0, 0.0000001)
    }

    @Test
    fun parseSlcanFrame_rejectsPreviousStandardStreamAndMalformedFrames() {
        val decoder = CanableTelemetryDecoder()

        assertEquals(null, decoder.parseSlcanFrame("T000004508F41A0300D2043F07"))
        assertEquals(null, decoder.parseSlcanFrame("t4507F41A0300D2043F"))
        assertEquals(null, decoder.decode(requireNotNull(decoder.parseSlcanFrame(slcanFrame(0x420, u16(1), u16(2), u16(3), u16(4))))))
    }

    @Test
    fun decode453_mapsZeroBrakeSwitchToReleased() {
        val decoder = CanableTelemetryDecoder()
        val frame = slcanFrame(0x453, u16(1350), u16(0), u16(0), u16(0))

        val packet = decoder.decode(requireNotNull(decoder.parseSlcanFrame(frame)))

        assertFalse(packet?.brakeSwitchApplied == true)
    }

    private fun slcanFrame(canId: Int, vararg bytes: Int): String {
        require(bytes.size == 8)
        return "t%03X8%s".format(canId, bytes.joinToString("") { "%02X".format(it and 0xFF) })
    }

    private fun u16(value: Int): IntArray {
        return intArrayOf(value and 0xFF, value ushr 8 and 0xFF)
    }

    private fun s16(value: Int): IntArray = u16(value and 0xFFFF)

    private fun s32(value: Int): IntArray {
        return intArrayOf(
            value and 0xFF,
            value ushr 8 and 0xFF,
            value ushr 16 and 0xFF,
            value ushr 24 and 0xFF
        )
    }

    private fun slcanFrame(canId: Int, vararg byteGroups: IntArray): String {
        return slcanFrame(canId, *byteGroups.flatMap { it.asIterable() }.toIntArray())
    }
}
