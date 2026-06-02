package com.loaloaloa.relay

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Seam over Firebase/Play-Services token acquisition so [WorkManagerRelayRegistrar]'s
 * registration decision is unit-testable and the "Firebase not configured / no Play Services"
 * case is observable rather than a silent no-op.
 */
interface FcmTokenProvider {
    /** True when Google Play Services is present and usable (FCM can deliver). */
    fun isFcmAvailable(): Boolean

    /** The current FCM registration token, or null if unavailable/not yet provisioned. */
    suspend fun currentToken(): String?
}

@Singleton
class PlayServicesFcmTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : FcmTokenProvider {

    override fun isFcmAvailable(): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun currentToken(): String? =
        try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Relay: FCM token unavailable")
            null
        }
}
