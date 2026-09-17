package org.eclipse.kuksa.testapp.domain

import org.eclipse.kuksa.proto.v1.Types as V1Types
import org.eclipse.kuksa.proto.v2.Types as V2Types

class DataTypeSerializationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object DataTypeSerializer {
    // The parse exceptions from toBooleanStrict/toFloat/toInt/etc. differ by type; catching all and rethrowing
    // as a typed DataTypeSerializationException keeps the caller's API clean.
    @Suppress("TooGenericExceptionCaught")
    fun toDatapoint(dataType: String, valueStr: String): V1Types.Datapoint {
        val builder = V1Types.Datapoint.newBuilder()
        try {
            when (dataType.lowercase()) {
                "boolean", "bool" -> builder.setBool(valueStr.toBooleanStrict())
                "float" -> builder.setFloat(valueStr.toFloat())
                "double" -> builder.setDouble(valueStr.toDouble())
                "int32", "int" -> builder.setInt32(valueStr.toInt())
                "uint32" -> builder.setUint32(valueStr.toInt())
                "int64", "long" -> builder.setInt64(valueStr.toLong())
                "uint64" -> builder.setUint64(valueStr.toLong())
                "string" -> builder.setString(valueStr)
                else -> builder.setString(valueStr)
            }
        } catch (e: Exception) {
            throw DataTypeSerializationException(
                "Failed to serialize '$valueStr' to data type '$dataType': ${e.message}",
                e,
            )
        }
        return builder.build()
    }

    // Same rationale as toDatapoint: heterogeneous parse exceptions unified under DataTypeSerializationException.
    @Suppress("TooGenericExceptionCaught")
    fun toValue(dataType: String, valueStr: String): V2Types.Value {
        val builder = V2Types.Value.newBuilder()
        try {
            when (dataType.lowercase()) {
                "boolean", "bool" -> builder.setBool(valueStr.toBooleanStrict())
                "float" -> builder.setFloat(valueStr.toFloat())
                "double" -> builder.setDouble(valueStr.toDouble())
                "int32", "int" -> builder.setInt32(valueStr.toInt())
                "uint32" -> builder.setUint32(valueStr.toInt())
                "int64", "long" -> builder.setInt64(valueStr.toLong())
                "uint64" -> builder.setUint64(valueStr.toLong())
                "string" -> builder.setString(valueStr)
                else -> builder.setString(valueStr)
            }
        } catch (e: Exception) {
            throw DataTypeSerializationException(
                "Failed to serialize '$valueStr' to data type '$dataType': ${e.message}",
                e,
            )
        }
        return builder.build()
    }
}
