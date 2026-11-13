# kotlinx.serialization: keep generated serializers for the API models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.atomix.launcher.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.atomix.launcher.**$$serializer { *; }

# Tink (EncryptedSharedPreferences) references annotation classes that are not on the runtime classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
