package app.toebeans.android.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import app.toebeans.android.R

/** One 24/7 animal poison-control hotline row on the Emergency card. */
data class EmergencyHotline(
    @StringRes val nameRes: Int,
    @StringRes val phoneRes: Int,
    @StringRes val detailRes: Int,
    val phoneDigits: String,
)

/**
 * Static, curated emergency contacts. Public emergency-contact information with the
 * ADR-0019 §3 framing (the app never claims medical judgment), no AI, no dynamic
 * content, no network: the dialer intent is user-initiated (ADR-0003).
 *
 * Numbers verified 2026-11-15 against the primary pages:
 *  - ASPCA Animal Poison Control: https://www.aspca.org/pet-care/animal-poison-control
 *  - Pet Poison Helpline: https://www.petpoisonhelpline.com/
 */
object EmergencyResources {
    val hotlines: List<EmergencyHotline> =
        listOf(
            EmergencyHotline(
                nameRes = R.string.emergency_aspca_name,
                phoneRes = R.string.emergency_aspca_phone,
                detailRes = R.string.emergency_aspca_detail,
                phoneDigits = "8884264435",
            ),
            EmergencyHotline(
                nameRes = R.string.emergency_pph_name,
                phoneRes = R.string.emergency_pph_phone,
                detailRes = R.string.emergency_pph_detail,
                phoneDigits = "8557647661",
            ),
        )

    /** tel: URI for the dialer. ACTION_DIAL only pre-fills the dialer; no permission. */
    fun telUri(phoneDigits: String): Uri = Uri.parse("tel:$phoneDigits")
}

/** Dialer intent for an emergency hotline. ACTION_DIAL (no CALL_PHONE permission). */
fun emergencyDialIntent(tel: Uri): Intent = Intent(Intent.ACTION_DIAL, tel)
