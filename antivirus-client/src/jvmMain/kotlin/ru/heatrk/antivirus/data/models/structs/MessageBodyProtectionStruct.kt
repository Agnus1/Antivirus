package ru.heatrk.antivirus.data.models.structs

import ru.heatrk.antivirus.utils.DynamicByteBuffer

class MessageBodyProtectionStruct : SerializableStruct() {
    var status: Byte = DISABLED

    override fun write(byteBuffer: DynamicByteBuffer) {
        byteBuffer.put(status)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageBodyProtectionStruct

        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        return status.hashCode()
    }

    override fun toString(): String {
        return "MessageBodyProtectionStruct(status=$status)"
    }

    companion object : Deserializer<MessageBodyProtectionStruct>() {
        const val ENABLED = 0.toByte()
        const val DISABLED = 1.toByte()

        override fun create(byteBuffer: DynamicByteBuffer) = MessageBodyProtectionStruct().apply {
            status = byteBuffer.get()
        }
    }
}