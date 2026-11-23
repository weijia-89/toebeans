package app.toebeans.android.data

import android.content.res.AssetManager
import app.toebeans.core.model.DoseUnit
import app.toebeans.core.model.parseDoseUnitOrDefault
import org.json.JSONObject

/**
 * One approved reference entry. Carries only a formulation and an optional unit from
 * a human-approved record. It never carries advice text, an amount, or a dose.
 */
public data class DrugReferenceEntry(
    public val medication: String,
    public val formulation: String,
    public val defaultUnit: DoseUnit?,
)

/** Lookup seam so the medication form can be tested without bundled data. */
public interface DrugReferenceReader {
    public fun find(medicationName: String): DrugReferenceEntry?
}

/** The no-data default. Absence of data never blocks entry. */
public object EmptyDrugReferenceReader : DrugReferenceReader {
    override fun find(medicationName: String): DrugReferenceEntry? = null
}

/**
 * Reads approved entries from the bundled assets directory that the approval gate
 * validates: [ENTRIES_PATH]. Uses AssetManager.open directly; there is no copy step
 * and no second path that could drift from this one.
 *
 * Every failure mode is a null result. A missing file, an empty file, malformed JSON,
 * an unknown unit, or an unexpected runtime error all degrade to "no entry", which
 * means no preselection and a full picker. The owner can always type an amount and
 * choose a unit by hand.
 */
public class DrugReferenceAssets(
    private val assets: AssetManager,
) : DrugReferenceReader {
    public override fun find(medicationName: String): DrugReferenceEntry? {
        val slug = slugify(medicationName)
        if (slug.isEmpty()) return null
        return runCatching {
            assets.open("$ENTRIES_PATH/$slug.json").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
                parseEntry(text)
            }
        }.getOrNull()
    }

    public companion object {
        /** Must match the path validated by scripts/test_drug_reference_approval.sh. */
        public const val ENTRIES_PATH: String = "drug-reference/entries"

        internal fun slugify(name: String): String =
            name
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')

        internal fun parseEntry(text: String): DrugReferenceEntry? {
            val obj = JSONObject(text)
            val medication = obj.optString("medication").trim()
            val formulation = obj.optString("formulation").trim()
            if (medication.isEmpty() || formulation.isEmpty()) return null
            val rawUnit = obj.optString("default_unit").trim()
            val defaultUnit =
                parseDoseUnitOrDefault(rawUnit.ifEmpty { null })
                    ?: DoseUnit.entries.firstOrNull { it.label.equals(rawUnit, ignoreCase = true) }
            return DrugReferenceEntry(medication, formulation, defaultUnit)
        }
    }
}
