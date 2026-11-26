# Music Stream Play Launcher — R8 rules.
#
# Compose, Room, Media3, Coil, Firebase and OkHttp ship their own consumer rules. Only contracts
# that this module creates through reflection or serialization are listed here.

# Keep line numbers so Crashlytics can de-obfuscate release stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep generated serializers of @Serializable navigation routes and payloads.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.streamplay.music.** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.streamplay.music.** {
    kotlinx.serialization.KSerializer serializer(...);
}
