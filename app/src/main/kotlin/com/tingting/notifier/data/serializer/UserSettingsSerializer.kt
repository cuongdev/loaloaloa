package com.tingting.notifier.data.serializer

import androidx.datastore.core.Serializer
import com.tingting.notifier.data.model.UserSettings
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.json.Json

/**
 * DataStore serializer for [UserSettings], encoding to JSON bytes via kotlinx.serialization.
 * Corrupt or empty input falls back to [defaultValue] so a bad file never crashes the app.
 */
object UserSettingsSerializer : Serializer<UserSettings> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: UserSettings = UserSettings()

    override suspend fun readFrom(input: InputStream): UserSettings =
        try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) defaultValue
            else json.decodeFromString(UserSettings.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            defaultValue
        }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        output.write(json.encodeToString(UserSettings.serializer(), t).encodeToByteArray())
    }
}
