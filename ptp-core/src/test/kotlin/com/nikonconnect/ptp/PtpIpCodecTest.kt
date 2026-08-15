package com.nikonconnect.ptp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PtpIpCodecTest {
    @Test
    fun operationRequestUsesLittleEndianWireFormat() {
        val packet = PtpIpCodec.encodeOperationRequest(
            operation = PtpOperationCode.GET_DEVICE_INFO,
            transactionId = 7u,
        )

        val (length, type) = PtpIpCodec.parseHeader(packet.copyOfRange(0, 8))
        assertEquals(packet.size, length)
        assertEquals(PtpIpPacketType.OPERATION_REQUEST, type)
        assertContentEquals(
            byteArrayOf(1, 0, 0, 0, 1, 16, 7, 0, 0, 0),
            packet.copyOfRange(8, packet.size),
        )
    }

    @Test
    fun parserRejectsOversizedPacket() {
        val header = PtpWriter()
            .writeUInt((PtpIpCodec.MAX_PACKET_LENGTH + 1).toUInt())
            .writeUInt(PtpIpPacketType.DATA.value)
            .toByteArray()
        assertFailsWith<PtpProtocolException> { PtpIpCodec.parseHeader(header) }
    }
}
