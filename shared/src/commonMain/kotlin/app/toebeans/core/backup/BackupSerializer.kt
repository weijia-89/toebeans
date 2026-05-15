package app.toebeans.core.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * JSON codec for [BackupExport]. Pure-KMP; no encryption.
 *
 * Wire format choices, explained:
 *  - **Field naming:** kotlinx-serialization default (Kotlin camelCase). We deliberately do NOT
 *    use a naming strategy that converts to snake_case — the on-disk format is internal to toebeans
 *    and matches the Kotlin source. (We accept the redundancy with [Medication.doseAmount] etc.)
 *  - **Pretty printing:** OFF in production. Saves bytes; the encrypted envelope obscures the
 *    contents anyway. Tests opt into pretty for readability.
 *  - **Unknown keys:** ignored on import. This is the forward-compat lever for [BackupExport.schemaVersion]
 *    bumps that add fields.
 *  - **Lenient parsing:** disabled. We control both sides of the wire; strict catches bugs.
 *  - **Encode defaults:** enabled. Otherwise null fields disappear and re-import would default
 *    them to non-null, causing drift.
 *
 * @sample app.toebeans.core.backup.BackupSerializerTest
 */
public class BackupSerializer(
    private val json: Json = DefaultJson,
) {
    /**
     * Serialize the export to a JSON string. The result is then handed to [BackupCipher] for
     * encryption; it is never written to disk in plain form by toebeans application code.
     */
    public fun encodeToString(export: BackupExport): String = json.encodeToString(BackupExport.serializer(), export)

    /**
     * Decode a JSON string into a [BackupExport]. The string is normally produced by [BackupCipher]
     * after decryption.
     *
     * @throws BackupFormatException if the JSON is malformed, the schema version is unsupported,
     *         or a required field is missing.
     */
    public fun decodeFromString(text: String): BackupExport {
        val parsed =
            try {
                json.decodeFromString(BackupExport.serializer(), text)
            } catch (e: SerializationException) {
                throw BackupFormatException("Backup JSON is malformed or fields are missing.", e)
            }
        if (parsed.schemaVersion > BackupExport.CURRENT_SCHEMA_VERSION) {
            throw BackupFormatException(
                "Backup schema version ${parsed.schemaVersion} is newer than this app supports " +
                    "(max ${BackupExport.CURRENT_SCHEMA_VERSION}). Upgrade the app, then re-import.",
            )
        }
        return parsed
    }

    public companion object {
        public val DefaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = false
                prettyPrint = false
            }

        public val PrettyJson: Json =
            Json(from = DefaultJson) {
                prettyPrint = true
            }
    }
}

/** Thrown when a backup payload is malformed, an unknown schema version, or missing fields. */
public class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
