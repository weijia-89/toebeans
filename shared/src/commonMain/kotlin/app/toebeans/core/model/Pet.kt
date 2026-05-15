package app.toebeans.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A pet under the user's care.
 *
 * @property id stable identifier (UUID v4 in canonical form, e.g. "f47ac10b-58cc-...").
 * @property name display name; not unique across pets.
 * @property species `dog` or `cat` at v1. The enum widens later; the persistence layer stores a
 *           free string so adding species is not a schema migration.
 * @property birthdate optional; required only when the user wants age-based UI features (none at v1).
 * @property weightKg optional; reserved for future dose-by-weight UX (not implemented at v1).
 * @property notes free-text owner notes.
 * @property createdAt creation timestamp (UTC).
 * @property archivedAt soft-delete marker. A pet with [archivedAt] != null is hidden from active
 *           views but its history is retained.
 */
@Serializable
public data class Pet(
    val id: String,
    val name: String,
    val species: Species,
    val birthdate: LocalDate?,
    val weightKg: Double?,
    val notes: String?,
    val createdAt: Instant,
    val archivedAt: Instant?,
) {
    init {
        require(id.isNotBlank()) { "Pet.id must not be blank" }
        require(name.isNotBlank()) { "Pet.name must not be blank" }
        weightKg?.let { require(it > 0.0) { "Pet.weightKg must be positive (was $it)" } }
    }

    public val isActive: Boolean get() = archivedAt == null
}

/**
 * Species enumeration. Stored as the lowercase string ([wireName]) in the database to keep schema
 * extension cheap; new entries are an additive enum change, not a migration.
 */
@Serializable
public enum class Species(
    public val wireName: String,
) {
    DOG("dog"),
    CAT("cat"),
    ;

    public companion object {
        public fun fromWireName(value: String): Species =
            entries.firstOrNull { it.wireName == value }
                ?: error("Unknown species '$value'. Add to Species enum and document in CHANGELOG.")
    }
}
