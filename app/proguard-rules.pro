# NetFetch Proguard / R8 Configuration for Google Play Store release

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep NetFetch model classes for serialization and reflection
-keep class com.netfetch.app.model.** { *; }

# Keep Android Service implementations
-keep class com.netfetch.app.service.** extends android.app.Service { *; }
-keep class com.netfetch.app.service.** extends android.net.VpnService { *; }

# Suppress warnings for optional libraries
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
