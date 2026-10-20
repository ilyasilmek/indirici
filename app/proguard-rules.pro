# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room entities, DAOs, and TypeConverters
-keep class com.example.data.local.** { *; }
-keep class com.example.data.model.** { *; }

# OkHttp & Coroutines safe rules
-dontwarn okhttp3.**
-dontwarn okio.**
