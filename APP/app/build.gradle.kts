plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.locctl.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.locctl.app"
        // minSdk 31（Android 12）：原计划 26，但 API 36 已移除
        // Criteria.POWER_REQUIREMENT_* 常量，废弃的 addTestProvider 重载无法编译；
        // 且本工具依赖的 Shizuku、前台服务类型、开发者选项模拟位置应用
        // 都要求 Android 12+。实测设备为 Android 16。
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // node 二进制与 ICU 等动态库合计约 93MB，必须分包
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // node 二进制不含调试符号，压缩后能显著减小 APK
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // assets 里的 js 不参与压缩，运行时用 AssetManager 直接读
    androidResources {
        noCompress += listOf("js", "gpx")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Shizuku：可选增强通道，探测不到时静默走 SDK
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
