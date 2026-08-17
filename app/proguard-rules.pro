# VoiceDAW ProGuard Rules
-keep class com.voicedaw.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
