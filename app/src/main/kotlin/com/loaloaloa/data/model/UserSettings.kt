package com.loaloaloa.data.model

import kotlinx.serialization.Serializable

/** How announcements are picked relative to the money direction. */
enum class SpeakOption { INCOME_ONLY, OUTGOING_ONLY, BOTH }

/** Audio stream the chime/speech plays on. */
enum class AudioOutput { NOTIFICATION, ALARM, MEDIA }

/** A daily quiet window in which announcements are suppressed (minutes-of-day). */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
)

/** Configuration for the polling API source (e.g. SePay). */
@Serializable
data class ApiConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val token: String = "",
    val account: String = "",
    val pollSeconds: Int = 30,
    /**
     * Provider transaction id of the most recent row ingested, used to dedupe the
     * polling source across restarts. Additive field; an empty default keeps
     * previously-persisted settings JSON loading cleanly.
     */
    val lastSeenTxnId: String = "",
)

/** Which money directions should be pushed to the outbound webhook. */
enum class WebhookTrigger { INCOME, OUTGOING, BOTH }

/**
 * Kind of outbound destination. [GENERIC] and [GOOGLE_SHEET] POST the flat JSON payload to
 * [WebhookConfig.url] (Google Sheet just adds in-app setup help for the Apps Script Web App);
 * [TELEGRAM] sends a formatted message via the Bot API using [WebhookConfig.botToken]/[chatId];
 * [GOOGLE_FORM] POSTs a form-urlencoded body to a Google Form's `formResponse` URL, mapping the
 * transaction fields onto the form's `entry.*` ids — no Apps Script / token needed.
 */
enum class WebhookType { GENERIC, TELEGRAM, GOOGLE_SHEET, GOOGLE_FORM }

/**
 * Configuration for one outbound destination. Multiple of these live in
 * [UserSettings.webhooks] and all fire together. When [enabled] and the fields its [type]
 * needs are present, each detected transaction matching [trigger] is delivered (via
 * WorkManager). For [WebhookType.GENERIC]/[WebhookType.GOOGLE_SHEET] the flat JSON payload
 * is POSTed to [url] with an optional `X-Webhook-Secret` [secret]; for
 * [WebhookType.TELEGRAM] a message is sent using [botToken] + [chatId]. [id] is a stable
 * key for editing/removing; [label] is an optional display name. Additive fields with
 * empty defaults keep previously-persisted settings JSON loading cleanly.
 */
@Serializable
data class WebhookConfig(
    val id: String = "",
    val label: String = "",
    val type: WebhookType = WebhookType.GENERIC,
    val enabled: Boolean = false,
    val url: String = "",
    val secret: String = "",
    val botToken: String = "",
    val chatId: String = "",
    /**
     * Google Form mapping (used only when [type] is [WebhookType.GOOGLE_FORM]): the `entry.*`
     * field id each transaction value is POSTed as. [url] holds the form's `formResponse` URL.
     * All four are parsed in one step from a pasted pre-filled link; blank when unused.
     */
    val formAmountEntry: String = "",
    val formBankEntry: String = "",
    val formTimeEntry: String = "",
    val formNoteEntry: String = "",
    val trigger: WebhookTrigger = WebhookTrigger.BOTH,
    /**
     * Optional work-shift window. When [shiftEnabled] this destination only fires while the
     * current minute-of-day is inside `[shiftStartMinutes, shiftEndMinutes)` (minutes 0..1439;
     * the window may wrap past midnight when start > end). Lets a shop forward notifications to
     * the staff who are actually on shift. Disabled by default → forwards around the clock; an
     * empty (start == end) window is treated as all-day so it never silently drops everything.
     */
    val shiftEnabled: Boolean = false,
    val shiftStartMinutes: Int = 8 * 60,
    val shiftEndMinutes: Int = 17 * 60,
)

/**
 * This device's part in a notification-relay "room". A [HUB] has the bank logged in, detects
 * transactions, and pushes them (E2E-encrypted) to the spokes; a [SPOKE] has no bank login and
 * only receives + announces what the hub forwards. [NONE] (the default) is a standalone device
 * that neither sends nor receives relayed transactions — preserving the pre-relay behaviour.
 */
enum class RelayRole { NONE, HUB, SPOKE }

/**
 * Observable outcome of the spoke's FCM-token registration with the room Sender. [IDLE] before any
 * attempt (or after unpair); [REGISTERED] once a token was acquired and the `/register` POST was
 * enqueued (WorkManager retries until it lands); [NO_FCM] when Firebase/Google Play Services is
 * unavailable on this device so no token can be obtained. Drives the staff connection-status card.
 * Additive field; an [IDLE] default keeps previously-persisted settings JSON loading cleanly.
 */
enum class RelayRegisterState { IDLE, REGISTERED, NO_FCM }

/**
 * Which UI shell this device shows, chosen on first launch and changeable via an explicit "switch
 * mode" action. [SHOP_OWNER] is the full app (bank-logged-in HUB or standalone). [STAFF] is the
 * pairing-first spoke shell. [UNSET] means no choice yet → show the mode picker. Orthogonal to
 * [RelayRole]: mode drives the shell, role drives the relay transport. Additive field; an [UNSET]
 * default keeps previously-persisted settings JSON loading cleanly (migrated once on first launch).
 */
enum class AppMode { UNSET, SHOP_OWNER, STAFF }

/**
 * Default Sender (Cloudflare Worker) URL pre-filled when a shop creates a relay room, so the common
 * case is one tap. The shop can still overwrite it in the hub field to point at another deployment.
 */
const val DEFAULT_RELAY_SENDER_URL = "https://loaloaloa.haveuever.workers.dev"

/**
 * Pairing for a relay room, shared by the hub and every spoke. [roomId] identifies the room on
 * the Sender; [roomSecret] is the base64url 256-bit key from which the E2E encryption and request
 * HMAC subkeys are derived (see [com.loaloaloa.relay.RelayCrypto]) — it never leaves a
 * paired device except inside the pairing QR; [senderUrl] is the relay endpoint (Cloudflare
 * Worker) both roles talk to. Empty defaults mean "not paired" and keep old settings JSON loading.
 */
@Serializable
data class RelayRoom(
    val roomId: String = "",
    val roomSecret: String = "",
    val senderUrl: String = "",
)

/** Root user-settings tree persisted via [androidx.datastore.core.DataStore]. */
@Serializable
data class UserSettings(
    val enableService: Boolean = false,
    val speakOption: SpeakOption = SpeakOption.BOTH,
    val excludedApps: List<String> = emptyList(),
    val speakShortMessage: Boolean = false,
    val audioOutput: AudioOutput = AudioOutput.NOTIFICATION,
    val forceMaxVolume: Boolean = false,
    val enableAudioFocus: Boolean = true,
    val speakInSilentMode: Boolean = false,
    val playChime: Boolean = true,
    val repeat: Boolean = false,
    /**
     * Append a running "Tổng hôm nay X đồng" sentence after each spoken income announcement,
     * using the calendar-day income total (incl. the transaction just received). Additive
     * boolean; a false default keeps previously-persisted settings JSON loading cleanly.
     */
    val speakDailyTotal: Boolean = false,
    /**
     * Also read the transaction's raw notification content aloud after the amount sentence
     * (e.g. the transfer memo the payer typed). Lets staff hear the "nội dung" without looking
     * at the screen. Additive boolean; a false default keeps previously-persisted settings JSON
     * loading cleanly.
     */
    val speakContent: Boolean = false,
    /**
     * Minimum amount (VND) a transaction must reach to be announced out loud. Transactions
     * below this are still persisted and forwarded to webhooks — only the speech is skipped.
     * 0 means announce every amount. Additive field; a 0 default preserves old behaviour.
     */
    val minAnnounceAmount: Long = 0,
    val quietHours: QuietHours = QuietHours(),
    val api: ApiConfig = ApiConfig(),
    /** Legacy single destination, kept for migration; new code uses [webhooks]. */
    val webhook: WebhookConfig = WebhookConfig(),
    /** All outbound destinations; every enabled+configured one fires per transaction. */
    val webhooks: List<WebhookConfig> = emptyList(),
    /**
     * Epoch-millis the current work shift started, or null when no shift is open. Set by the
     * in-app "Chốt ca" (shift handover) flow: the staff opens a shift, every transaction from
     * this instant onward counts toward the running shift total, and closing the shift clears
     * it back to null. Additive nullable field; a null default keeps previously-persisted
     * settings JSON loading cleanly.
     */
    val shiftStartedAt: Long? = null,
    /**
     * Default employee name on THIS device (set once, editable). Used to prefill the "Bắt đầu ca"
     * dialog in the staff shell and as the prompt-to-set-name signal when blank. Additive field;
     * an empty default keeps previously-persisted settings JSON loading cleanly.
     */
    val staffName: String = "",
    /**
     * Employees currently on shift on THIS device. Every transaction ingested while this is
     * non-empty gets their names stamped onto its note (see [com.loaloaloa.ingest.StaffNote]).
     * One device may hold several names (shared counter); each device tags only its own. Cleared
     * when the shift closes. Additive field; an empty default keeps old settings JSON loading cleanly.
     */
    val activeStaff: List<String> = emptyList(),
    /**
     * User-added notification sources beyond the built-in [com.loaloaloa.parser.BankRegistry]
     * whitelist: package name → display name captured at add-time (so the notification path needs no
     * PackageManager lookup). Notifications from these packages are parsed like any bank's — they only
     * yield a transaction when their text carries a recognizable amount. Additive field; an empty
     * default keeps previously-persisted settings JSON loading cleanly.
     */
    val customApps: Map<String, String> = emptyMap(),
    /**
     * This device's role in a notification-relay room (see [RelayRole]). [RelayRole.NONE] by
     * default so existing standalone installs behave exactly as before. Additive field.
     */
    val relayRole: RelayRole = RelayRole.NONE,
    /**
     * The paired relay room ([RelayRoom]), or its empty default when this device isn't paired.
     * Holds the room id, shared secret, and Sender URL used by both hub and spoke. Additive
     * field; an empty default keeps previously-persisted settings JSON loading cleanly.
     */
    val relayRoom: RelayRoom = RelayRoom(),
    val relayRegisterState: RelayRegisterState = RelayRegisterState.IDLE,
    /** UI-shell selection (see [AppMode]); [UNSET] shows the mode picker. Additive. */
    val appMode: AppMode = AppMode.UNSET,
    /**
     * Settings schema version, bumped to 1 the first time new code touches the tree. Old payloads
     * (and fresh defaults) read as 0; the one-time mode migration runs while this is < 1. Additive.
     */
    val settingsVersion: Int = 0,
)

/**
 * The destinations to actually fire. New setups populate [UserSettings.webhooks]; users who
 * configured the pre-list single [UserSettings.webhook] are migrated on the fly (and persisted
 * on their next save) so they keep firing without losing their setup.
 */
val UserSettings.effectiveWebhooks: List<WebhookConfig>
    get() = webhooks.ifEmpty {
        if (webhook.url.isNotBlank() || webhook.enabled) {
            listOf(webhook.copy(id = "legacy", type = WebhookType.GENERIC))
        } else {
            emptyList()
        }
    }
