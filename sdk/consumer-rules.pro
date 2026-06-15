# InfluTo SDK consumer ProGuard/R8 rules (member-level only; no global flags).
# Keep the public API and result models in case a host serializes/reflects on them.
-keep public class to.influ.sdk.InfluTo { public *; }
-keep public class to.influ.sdk.InfluToConfig { *; }
-keep public class to.influ.sdk.** { *; }
# RevenueCat (optional) ships its own consumer rules; nothing needed here.
