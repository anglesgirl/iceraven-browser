-dontobfuscate

####################################################################################################
# GeckoView built-ins
####################################################################################################

-dontwarn org.mozilla.geckoview.**

# Raptor now writes a *-config.yaml file to specify Gecko runtime settings (e.g. the profile dir). This
# file gets deserialized into a DebugConfig object, which is why we need to keep this class
# and its members.
-keep class org.mozilla.gecko.util.DebugConfig { *; }

####################################################################################################
# Remove debug logs from release builds
####################################################################################################
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

####################################################################################################
# Mozilla Application Services
####################################################################################################

-keep class mozilla.appservices.** { *; }

# Keep code generated from Glean Metrics
-keep class org.mozilla.fenix.GleanMetrics.** {  *; }

####################################################################################################
# Firebase Analytics
####################################################################################################

# Keep Firebase Analytics classes from being stripped by R8
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep AnalyticsTracker class
-keep class org.mozilla.fenix.utils.AnalyticsTracker { *; }
-keep class org.mozilla.fenix.utils.AnalyticsTracker$* { *; }

# Keep Firebase Analytics API
-keep @com.google.firebase.annotations.PublicApi class *
-keep @androidx.annotation.Keep class *

# Keep classes referenced by reflection (Firebase uses reflection internally)
-keep class com.google.firebase.analytics.** { *; }
