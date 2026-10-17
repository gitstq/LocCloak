-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# JNI native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# PairingContext is looked up via JNI in libadb.so
-keep class moe.shizuku.manager.adb.PairingContext { *; }

# HiddenApiBypass and Conscrypt reflection
-keep class org.lsposed.hiddenapibypass.** { *; }

# BouncyCastle classes used by AdbKey
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-dontwarn org.bouncycastle.**

# Kotlin Coroutines and Compose
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
