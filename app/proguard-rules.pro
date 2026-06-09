# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep the Goose JNI bridge — prevents obfuscation of native method names
-keep class com.goose.android.rust.GooseRustBridge {
    native <methods>;
    public *;
}

# Keep all BLE and data model classes
-keep class com.goose.android.ble.** { *; }
-keep class com.goose.android.data.** { *; }

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Kotlin serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
