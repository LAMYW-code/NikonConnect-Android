package com.nikonconnect.ptp

enum class PtpIpPacketType(val value: UInt) {
    INIT_COMMAND_REQUEST(0x00000001u),
    INIT_COMMAND_ACK(0x00000002u),
    INIT_EVENT_REQUEST(0x00000003u),
    INIT_EVENT_ACK(0x00000004u),
    INIT_FAIL(0x00000005u),
    OPERATION_REQUEST(0x00000006u),
    OPERATION_RESPONSE(0x00000007u),
    EVENT(0x00000008u),
    START_DATA(0x00000009u),
    DATA(0x0000000Au),
    CANCEL(0x0000000Bu),
    END_DATA(0x0000000Cu),
    PROBE_REQUEST(0x0000000Du),
    PROBE_RESPONSE(0x0000000Eu);

    companion object {
        fun fromValue(value: UInt): PtpIpPacketType =
            entries.firstOrNull { it.value == value }
                ?: throw PtpProtocolException("未知 PTP/IP 包类型：0x${value.toString(16)}")
    }
}

enum class PtpOperationCode(val value: UShort) {
    GET_DEVICE_INFO(0x1001u),
    OPEN_SESSION(0x1002u),
    CLOSE_SESSION(0x1003u),
    GET_STORAGE_IDS(0x1004u),
    GET_STORAGE_INFO(0x1005u),
    GET_OBJECT_HANDLES(0x1007u),
    GET_OBJECT_INFO(0x1008u),
    GET_OBJECT(0x1009u),
    GET_THUMB(0x100Au),
    GET_PARTIAL_OBJECT(0x101Bu),
    GET_OBJECTS_METADATA(0x9434u),
}

object PtpResponseCode {
    const val OK: UShort = 0x2001u
    const val OPERATION_NOT_SUPPORTED: UShort = 0x2005u
    const val DEVICE_BUSY: UShort = 0x2019u
}

data class PtpIpPacket(
    val type: PtpIpPacketType,
    val payload: ByteArray,
)

data class PtpResponse(
    val code: UShort,
    val transactionId: UInt,
    val parameters: List<UInt>,
)

data class PtpDeviceInfo(
    val manufacturer: String?,
    val model: String?,
    val deviceVersion: String?,
    val serialNumber: String?,
    val operationsSupported: Set<UShort>,
) {
    fun supports(operation: PtpOperationCode): Boolean = operation.value in operationsSupported
}

data class PtpInitCommandAck(
    val connectionNumber: UInt,
    val responderFriendlyName: String,
    val protocolVersion: UInt,
)

class PtpProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
