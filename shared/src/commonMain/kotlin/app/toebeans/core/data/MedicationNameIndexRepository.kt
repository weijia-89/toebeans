package app.toebeans.core.data

/**
 * Repository for searching medication names from a local index.
 *
 * Supports case-insensitive prefix and substring matching. Results are limited
 * to keep UI dropdowns compact. Free-form entry is allowed - this index is
 * for suggestions only, not a whitelist.
 *
 * @see InMemoryMedicationNameIndex
 */
public interface MedicationNameIndexRepository {
    /**
     * Search for medication names matching the query.
     *
     * @param query Search string (case-insensitive)
     * @param limit Maximum number of results to return (default: 10)
     * @return List of matching medication names, sorted alphabetically
     */
    public suspend fun search(
        query: String,
        limit: Int = 10,
    ): List<String>

    /**
     * Get all medication names in the index.
     *
     * Useful for pre-populating dropdowns or batch operations.
     *
     * @return Full list of medication names, sorted alphabetically
     */
    public suspend fun getAll(): List<String>
}
