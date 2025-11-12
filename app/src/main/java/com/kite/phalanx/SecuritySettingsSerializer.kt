package com.kite.phalanx

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for SecuritySettings Proto DataStore.
 *
 * Handles serialization/deserialization of SecuritySettings protobuf messages.
 */
object SecuritySettingsSerializer : Serializer<SecuritySettings> {
    override val defaultValue: SecuritySettings = SecuritySettings.getDefaultInstance()
        .toBuilder()
        .setSensitivity(SecuritySettings.SensitivityLevel.MEDIUM)
        .setOtpPassthrough(true)
        .setSenderPackRegion("IN")
        .setTfliteClassifierEnabled(false)
        .build()

    override suspend fun readFrom(input: InputStream): SecuritySettings {
        try {
            return SecuritySettings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(
        t: SecuritySettings,
        output: OutputStream
    ) = t.writeTo(output)
}
