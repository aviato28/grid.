# kotlinx.serialization: keep generated serializers of our @Serializable models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.nbansal.grid.** {
    *** Companion;
}
-keepclasseswithmembers class com.nbansal.grid.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nbansal.grid.**$$serializer { *; }

# Install result receiver is referenced from the manifest and by PackageInstaller callbacks
-keep class com.nbansal.grid.update.InstallResultReceiver { *; }

# org.json is part of the Android framework; nothing to keep.
