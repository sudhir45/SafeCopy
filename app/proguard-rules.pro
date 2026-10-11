# Spec 14.4: strip verbose/debug/info logging from release builds. SafeLog is already a no-op
# outside debug builds (BuildConfig.DEBUG is a constant R8 folds away); this also catches any
# library or stray call.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Compose, DataStore and AndroidX ship their own consumer rules.

# ML Kit creates its component registrars by reflection (listed as metadata on
# MlKitComponentDiscoveryService). Without these rules R8 strips their no-arg constructors,
# every registrar fails to load, and detection silently finds nothing in release builds
# (seen on the API 26 emulator: "NoSuchMethodException: <init> []").
-keep class * implements com.google.firebase.components.ComponentRegistrar { public <init>(); }
-keep class com.google.mlkit.**.*Registrar { public <init>(); }
