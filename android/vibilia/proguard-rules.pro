# kotlinx.serialization: keep generated serializers of the row models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.numynox.nexus.**$$serializer { *; }

# Ktor and supabase-kt reference optional JVM classes that Android does not have.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.**

# MapLibre's native code calls back into these by name.
-keep class org.maplibre.** { *; }
