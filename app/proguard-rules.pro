# Mirror Natural Look — ProGuard rules

# Keep billing classes
-keep class com.android.billingclient.** { *; }

# Keep AdMob
-keep class com.google.android.gms.ads.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep app classes
-keep class com.mirrornaturallook.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
