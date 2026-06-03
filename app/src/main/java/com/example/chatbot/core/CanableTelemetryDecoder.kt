package com.example.chatbot.core

import com.example.chatbot.models.TelemetryPacket

/**
 * Converts SLCAN text frames from the CANable adapter into the app's cumulative
 * telemetry packet model.
 */
class CanableTelemetryDecoder {
    private var sequence = 0L
    private var latest = TelemetryPacket()

    /**
     * Parses a standard SLCAN data frame such as `t4508...` into a typed frame.
     * Unsupported frame types, malformed hex, and non-8-byte payloads return null.
     */
    fun parseSlcanFrame(line: String): SlcanFrame? {
        val trimmed = line.trim()
        if (trimmed.length < MIN_FRAME_LENGTH || trimmed.first() != 't') return null

        val canId = trimmed.substring(1, 4).toIntOrNull(radix = 16) ?: return null
        val dlc = trimmed.substring(4, 5).toIntOrNull(radix = 16) ?: return null
        if (dlc != SPEC_DLC) return null

        val payloadEnd = 5 + dlc * 2
        if (trimmed.length < payloadEnd) return null

        val data = hexToBytes(trimmed.substring(5, payloadEnd)) ?: return null
        return SlcanFrame(canId = canId, dlc = dlc, data = data)
    }

    /**
     * Applies the decode table for supported CAN IDs and returns the latest
     * cumulative telemetry packet, or null when the frame is not recognized.
     */
    fun decode(frame: SlcanFrame): TelemetryPacket? {
        if (frame.dlc != SPEC_DLC) return null

        latest = when (frame.canId) {
            0x450 -> decode450(frame.data)
            0x451 -> decode451(frame.data)
            0x452 -> decode452(frame.data)
            0x453 -> decode453(frame.data)
            0x454 -> decode454(frame.data)
            0x455 -> decode455(frame.data)
            0x456 -> decode456(frame.data)
            0x457 -> decode457(frame.data)
            0x458 -> decode458(frame.data)
            0x459 -> decode459(frame.data)
            else -> return null
        }.copy(sequence = ++sequence, timestamp = System.currentTimeMillis() / 1000.0)

        return latest
    }

    /**
     * Decodes engine speed, gear, vehicle speed, and coolant temperature.
     */
    private fun decode450(data: ByteArray): TelemetryPacket {
        val vehicleSpeedMph = u16(data, 4) * 0.1
        return latest.copy(
            rpm = u16(data, 0).toDouble(),
            gear = u16(data, 2),
            speed = vehicleSpeedMph,
            waterTempF = u16(data, 6) * 0.1
        )
    }

    /**
     * Decodes individual wheel speeds and derives each wheel's speed delta from
     * the four-wheel average.
     */
    private fun decode451(data: ByteArray): TelemetryPacket {
        val fl = u16(data, 0) * 0.1
        val fr = u16(data, 2) * 0.1
        val rl = u16(data, 4) * 0.1
        val rr = u16(data, 6) * 0.1
        val averageWheelSpeed = (fl + fr + rl + rr) / 4.0
        return latest.copy(
            wheelSpeedFlMph = fl,
            wheelSpeedFrMph = fr,
            wheelSpeedRlMph = rl,
            wheelSpeedRrMph = rr,
            wheelSpeedDeltas = listOf(
                fl - averageWheelSpeed,
                fr - averageWheelSpeed,
                rl - averageWheelSpeed,
                rr - averageWheelSpeed
            )
        )
    }

    /**
     * Decodes engine oil temperature and drive-by-wire pedal position.
     */
    private fun decode452(data: ByteArray): TelemetryPacket {
        val ecuOilTemp = u16(data, 0) * 0.1
        val pedalPosition = percent(u16(data, 6) * 0.01)
        return latest.copy(
            engineOilTempF = ecuOilTemp,
            ecuDbwApp1Percent = pedalPosition,
            throttle = pedalPosition,
            pedalPositionPercent = pedalPosition
        )
    }

    /**
     * Decodes fuel level and brake switch state.
     */
    private fun decode453(data: ByteArray): TelemetryPacket {
        return latest.copy(
            fuelLevelGallons = u16(data, 0) * 0.01,
            brakeSwitchApplied = u16(data, 6) == 1
        )
    }

    /**
     * Decodes ECU malfunction indicator output state.
     */
    private fun decode454(data: ByteArray): TelemetryPacket {
        return latest.copy(
            ecuMilOut = u16(data, 0)
        )
    }

    /**
     * Decodes three-axis acceleration in g.
     */
    private fun decode455(data: ByteArray): TelemetryPacket {
        return latest.copy(
            inlineAccelG = s16(data, 0) * 0.01,
            lateralAccelG = s16(data, 2) * 0.01,
            verticalAccelG = s16(data, 4) * 0.01
        )
    }

    /**
     * Decodes roll, pitch, and yaw rates in degrees per second.
     */
    private fun decode456(data: ByteArray): TelemetryPacket {
        return latest.copy(
            rollRateDps = s16(data, 0) * 0.1,
            pitchRateDps = s16(data, 2) * 0.1,
            yawRateDps = s16(data, 4) * 0.1
        )
    }

    /**
     * Decodes oil pressure, GPS speed, fuel pressure, and brake pressure.
     */
    private fun decode457(data: ByteArray): TelemetryPacket {
        val gpsSpeedMph = u16(data, 2) * 0.1
        val brakePressure = u16(data, 6).toDouble()
        return latest.copy(
            oilPressurePsi = u16(data, 0) * 0.1,
            speed = gpsSpeedMph,
            fuelPressurePsi = u16(data, 4) * 0.1,
            brakePressurePsi = brakePressure,
            brake = brakePressure
        )
    }

    /**
     * Decodes analog oil temperature and mirrors it into the oil filter field.
     */
    private fun decode458(data: ByteArray): TelemetryPacket {
        val analogOilTemp = u16(data, 0) * 0.1
        return latest.copy(
            analogOilTempF = analogOilTemp,
            oilFilterTempF = analogOilTemp
        )
    }

    /**
     * Decodes GPS latitude and longitude in decimal degrees.
     */
    private fun decode459(data: ByteArray): TelemetryPacket {
        return latest.copy(
            latitude = s32(data, 0) * 0.0000001,
            longitude = s32(data, 4) * 0.0000001
        )
    }

    /**
     * Reads an unsigned little-endian 16-bit integer from the payload.
     */
    private fun u16(data: ByteArray, offset: Int): Int {
        return ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset].toInt() and 0xFF)
    }

    /**
     * Reads a signed little-endian 16-bit integer from the payload.
     */
    private fun s16(data: ByteArray, offset: Int): Int {
        return u16(data, offset).toShort().toInt()
    }

    /**
     * Reads a signed little-endian 32-bit integer from the payload.
     */
    private fun s32(data: ByteArray, offset: Int): Int {
        return ((data[offset + 3].toInt() and 0xFF) shl 24) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset].toInt() and 0xFF)
    }

    /**
     * Clamps a decoded percentage to the valid 0-100 range.
     */
    private fun percent(value: Double): Double {
        return value.coerceIn(0.0, 100.0)
    }

    /**
     * Converts an even-length hexadecimal string into bytes, returning null for
     * invalid hex input.
     */
    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { index ->
            val byteText = hex.substring(index * 2, index * 2 + 2)
            byteText.toIntOrNull(radix = 16)?.toByte() ?: return null
        }
    }

    companion object {
        val decodeConfiguration: List<CanDecodeConfig> = listOf(
            CanDecodeConfig("0x450", "0-1", "ECU RPM", "Unsigned 16 LE", "value * 1", "rpm"),
            CanDecodeConfig("0x450", "2-3", "ECU GEAR", "Unsigned 16 LE", "value * 1", "gear"),
            CanDecodeConfig("0x450", "4-5", "ECU VEH SPD", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x450", "6-7", "ECU ECT", "Unsigned 16 LE", "value * 0.1", "deg F"),
            CanDecodeConfig("0x451", "0-1", "ECU W SPD FL", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x451", "2-3", "ECU W SP FR", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x451", "4-5", "ECU W SPD RL", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x451", "6-7", "ECU W SPD RR", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x452", "0-1", "ECU OIL T", "Unsigned 16 LE", "value * 0.1", "deg F"),
            CanDecodeConfig("0x452", "6-7", "ECU DBW APP1", "Unsigned 16 LE", "value * 0.01", "%"),
            CanDecodeConfig("0x453", "0-1", "Fuel Analog", "Unsigned 16 LE", "value * 0.01", "gal"),
            CanDecodeConfig("0x453", "6-7", "ECU BRK SW", "Unsigned 16 LE", "0=released, 1=applied", "binary"),
            CanDecodeConfig("0x454", "0-1", "ECU MIL OUT", "Unsigned 16 LE", "value * 1", "#"),
            CanDecodeConfig("0x455", "0-1", "InlineAcc AccX", "Signed 16 LE", "value * 0.01", "g"),
            CanDecodeConfig("0x455", "2-3", "LateralAcc AccY", "Signed 16 LE", "value * 0.01", "g"),
            CanDecodeConfig("0x455", "4-5", "VerticalAcc AccZ", "Signed 16 LE", "value * 0.01", "g"),
            CanDecodeConfig("0x456", "0-1", "RollRate GyroX", "Signed 16 LE", "value * 0.1", "deg/s"),
            CanDecodeConfig("0x456", "2-3", "PitchRate GyroY", "Signed 16 LE", "value * 0.1", "deg/s"),
            CanDecodeConfig("0x456", "4-5", "YawRate GyroZ", "Signed 16 LE", "value * 0.1", "deg/s"),
            CanDecodeConfig("0x457", "0-1", "OilPressure Analog", "Unsigned 16 LE", "value * 0.1", "psi"),
            CanDecodeConfig("0x457", "2-3", "GPS Speed", "Unsigned 16 LE", "value * 0.1", "mph"),
            CanDecodeConfig("0x457", "4-5", "ECU FUEL P", "Unsigned 16 LE", "value * 0.1", "psi"),
            CanDecodeConfig("0x457", "6-7", "Brake Pressure Analog", "Unsigned 16 LE", "value * 1", "psi"),
            CanDecodeConfig("0x458", "0-1", "Oil Temp Analog", "Unsigned 16 LE", "value * 0.1", "deg F"),
            CanDecodeConfig("0x459", "0-3", "GPS Latitude", "Signed 32 LE", "value * 0.0000001", "decimal deg"),
            CanDecodeConfig("0x459", "4-7", "GPS Longitude", "Signed 32 LE", "value * 0.0000001", "decimal deg")
        )

        private const val SPEC_DLC = 8
        private const val MIN_FRAME_LENGTH = 21
    }
}
