package com.sudhir.safecopy.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sudhir.safecopy.domain.detect.AadhaarMaskMode
import com.sudhir.safecopy.domain.detect.DetectionOptions
import com.sudhir.safecopy.domain.detect.PanMaskMode
import com.sudhir.safecopy.domain.model.WatermarkConfig
import com.sudhir.safecopy.domain.model.WatermarkStyle
import com.sudhir.safecopy.domain.model.WatermarkTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Spec 11, minus the P1 keys (refCodeEnabled, footerStrip, keepShareLog). */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val defaultTemplate: WatermarkTemplate = WatermarkTemplate.KYC,
    val watermarkStyle: WatermarkStyle = WatermarkStyle(),
    val uppercase: Boolean = true,
    val aadhaarMaskMode: AadhaarMaskMode = AadhaarMaskMode.FIRST_8,
    val panMaskMode: PanMaskMode = PanMaskMode.FIRST_6,
    val maskQrByDefault: Boolean = true,
) {
    val detectionOptions get() = DetectionOptions(aadhaarMaskMode, panMaskMode, maskQrByDefault)

    fun newWatermark(date: LocalDate) =
        WatermarkConfig(template = defaultTemplate, date = date, uppercase = uppercase, style = watermarkStyle)
}

/**
 * App settings in DataStore (spec 11). DataStore lives in app-private files, which the backup
 * rules exclude (spec 2.9).
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    val recentRecipients: Flow<List<String>> = store.data.map { RecentRecipients.decode(it[KEY_RECENTS]) }

    suspend fun addRecentRecipient(recipient: String) {
        store.edit { prefs ->
            prefs[KEY_RECENTS] = RecentRecipients.encode(
                RecentRecipients.add(RecentRecipients.decode(prefs[KEY_RECENTS]), recipient),
            )
        }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[KEY_ONBOARDING_DONE] = s.onboardingDone
            prefs[KEY_TEMPLATE] = s.defaultTemplate.name
            prefs[KEY_OPACITY] = s.watermarkStyle.opacity
            prefs[KEY_ANGLE] = s.watermarkStyle.angleDegrees
            prefs[KEY_SIZE] = s.watermarkStyle.size.name
            prefs[KEY_COLOUR] = s.watermarkStyle.colour.name
            prefs[KEY_UPPERCASE] = s.uppercase
            prefs[KEY_AADHAAR_MODE] = s.aadhaarMaskMode.name
            prefs[KEY_PAN_MODE] = s.panMaskMode.name
            prefs[KEY_MASK_QR] = s.maskQrByDefault
        }
    }

    /** "Clear all data" (spec 11): every setting and every recent recipient. */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = this[KEY_ONBOARDING_DONE] ?: d.onboardingDone,
            defaultTemplate = enumOr(this[KEY_TEMPLATE], d.defaultTemplate),
            watermarkStyle = WatermarkStyle(
                opacity = (this[KEY_OPACITY] ?: d.watermarkStyle.opacity)
                    .coerceIn(WatermarkStyle.MIN_OPACITY, WatermarkStyle.MAX_OPACITY),
                angleDegrees = (this[KEY_ANGLE] ?: d.watermarkStyle.angleDegrees)
                    .coerceIn(WatermarkStyle.MIN_ANGLE, WatermarkStyle.MAX_ANGLE),
                size = enumOr(this[KEY_SIZE], d.watermarkStyle.size),
                colour = enumOr(this[KEY_COLOUR], d.watermarkStyle.colour),
            ),
            uppercase = this[KEY_UPPERCASE] ?: d.uppercase,
            aadhaarMaskMode = enumOr(this[KEY_AADHAAR_MODE], d.aadhaarMaskMode),
            panMaskMode = enumOr(this[KEY_PAN_MODE], d.panMaskMode),
            maskQrByDefault = this[KEY_MASK_QR] ?: d.maskQrByDefault,
        )
    }

    /** Unknown or missing names fall back to the default (e.g. after an enum is renamed). */
    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private companion object {
        // Ordered list, so a joined string rather than the string set spec 11 sketches.
        val KEY_RECENTS = stringPreferencesKey("recentRecipients")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboardingDone")
        val KEY_TEMPLATE = stringPreferencesKey("defaultTemplate")
        val KEY_OPACITY = floatPreferencesKey("watermarkOpacity")
        val KEY_ANGLE = floatPreferencesKey("watermarkAngle")
        val KEY_SIZE = stringPreferencesKey("watermarkSize")
        val KEY_COLOUR = stringPreferencesKey("watermarkColour")
        val KEY_UPPERCASE = booleanPreferencesKey("uppercase")
        val KEY_AADHAAR_MODE = stringPreferencesKey("aadhaarMaskMode")
        val KEY_PAN_MODE = stringPreferencesKey("panMaskMode")
        val KEY_MASK_QR = booleanPreferencesKey("maskQrByDefault")
    }
}
