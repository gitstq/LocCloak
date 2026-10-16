# Shizuku 通过 AIDL 反射调用，保留其接口
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

# 保留 aidl 生成的 Binder  stub
-keep class * extends android.os.Binder { *; }

# Kotlin 协程
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**
