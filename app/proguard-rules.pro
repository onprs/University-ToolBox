# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.onprs.universitytoolbox.**$$serializer { *; }
-keepclassmembers class io.github.onprs.universitytoolbox.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.onprs.universitytoolbox.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JNI Native Methods & Onprs-Encryption 桥接层
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class io.github.onprs.encryption.engine.NativeLicenseBridge { *; }
-keep class io.github.onprs.encryption.engine.NativeVerificationSnapshot { *; }

# Room 数据库实体与 DAO
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Google Tink & Android Keystore
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**

# ML Kit Barcode Scanning & CameraX
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp & Kotlin Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
