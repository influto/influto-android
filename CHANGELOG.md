# Changelog

All notable changes to the InfluTo Android SDK are documented here. This project
adheres to [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-08-09

### Fixed
- `checkAttribution()` no longer re-fires `/sdk/track-install` on every cold
  start for organic users: the organic (non-attributed) result is now persisted
  like the attributed one (contract 1.6.0). A failed request still retries on
  the next launch.

### Added
- Persisted per-install UUID (`@influto/install_id`) sent as `device_id`, so
  the backend counts unique devices instead of launches. No permissions, no
  fingerprinting; resets on reinstall by design.
- Deterministic `eventId` for once-only monetization events (`trial_started`,
  `subscription_purchased`, `subscription_renewed`) derived from
  (event type, user, properties) — cross-launch re-fires collapse server-side.

## [1.0.0] - 2026-06-15

### Added
- Initial public release of `to.influ:android-sdk`.
- Influencer attribution: `initialize`, `checkAttribution` (install attribution),
  `identifyUser`, `trackEvent` (UUID-v4 idempotency), `getActiveCampaigns`,
  `validateCode`, `setReferralCode`, `applyCode`, `getReferralCode`,
  `getPrefilledCode`, `clearAttribution`.
- Store-direct purchase validation: `reportPurchase(purchaseToken, ...)` posts the
  Google Play Billing purchase token to `/sdk/purchase` (no RevenueCat required).
- Optional RevenueCat integration with no hard dependency: a caller-injected
  attribute-setter callback, plus a reflection fallback. Sets `influto_code` and
  `influto_referral="true"` on attribution / `setReferralCode`.
- Every network method ships as both a `suspend` function and a `Result`-callback
  overload for Java / non-coroutine call sites.
- New `to.influ:android-sdk-ui` Compose artifact with `InfluToReferralCodeInput` —
  a pre-built, debounced referral-code input. Campaign and referrer names are OFF
  by default (`showCampaignName` / `showReferrerName` both default `false`).
- New `to.influ:android-sdk-billing` Play Billing auto-capture artifact (keeps the
  core `:sdk` free of any Play Billing dependency). When on the classpath, `initialize`
  reflectively starts purchase auto-capture for store-direct apps. Configured via the
  new `InfluToConfig.autoCapture` (default `true`; store-direct apps only, RevenueCat
  apps unaffected) and `InfluToConfig.oneTimeProductIds` (declare one-time / consumable
  Play SKUs so they route to NON_RENEWING validation) options.

[1.0.0]: https://github.com/influto/influto-android/releases/tag/1.0.0
