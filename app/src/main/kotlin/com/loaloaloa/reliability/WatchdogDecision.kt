package com.loaloaloa.reliability

/**
 * Pure decision for the reliability watchdog: should we nudge (rebind) the
 * notification listener right now? We rebind only when the user wants the service
 * running AND notification access is still granted — rebinding a component the user
 * has revoked access to is pointless churn. Holds no Android types so it is
 * unit-tested on the JVM.
 */
object WatchdogDecision {

    /**
     * @param enableService the user's `UserSettings.enableService` flag.
     * @param accessGranted whether notification-listener access is currently granted.
     * @return true when the watchdog should force a rebind of the listener.
     */
    fun shouldRebind(enableService: Boolean, accessGranted: Boolean): Boolean =
        enableService && accessGranted
}
