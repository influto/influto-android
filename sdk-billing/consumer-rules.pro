# Play Billing ships its own consumer ProGuard rules; nothing extra needed for
# InfluToBilling (no reflection, no serialization). Keep the public entry points.
-keep class to.influ.sdk.billing.InfluToBilling { public *; }
-keep class to.influ.sdk.billing.PurchaseSyncResult { public *; }
