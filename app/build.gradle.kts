plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

android {
    namespace = "com.upuaut.xposedsearch"
    compileSdk = 37

    lint {
        disable += "NullSafeMutableLiveData"
    }

    defaultConfig {
        applicationId = "com.upuaut.xposedsearch"
        minSdk = 33
        targetSdk = 37
        versionCode = 25
        versionName = "2.0.5-coui"

        // 只保留 arm64-v8a：本模块目标是 COUI(ColorOS 17) 的 64 位 OPPO/OnePlus/Realme 机型
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Release 签名：优先读环境变量 / local.properties，缺省回落本机 CI key（可用 ETA_KEYSTORE 覆盖）
    signingConfigs {
        create("release") {
            val storePath = (project.findProperty("ETA_KEYSTORE") as String?)
                ?: System.getenv("ETA_KEYSTORE")
                ?: "/workspace/keys/heytap.jks"
            storeFile = file(storePath)
            storePassword = System.getenv("ETA_KS_PASS") ?: "heytap2026"
            keyAlias = System.getenv("ETA_KS_ALIAS") ?: "heytap"
            keyPassword = System.getenv("ETA_KEY_PASS") ?: "heytap2026"
        }
    }

    buildTypes {
        release {
            // R8：压缩 + 混淆（Compose/COUI 运行时极大，这里能省下大头）
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))

    // COUI 1.1.0 = ColorOS 17 设计语言（Compose Multiplatform），替换 miuix
    implementation(libs.coui.ui)
    implementation(libs.coui.core)
    implementation(libs.coui.squircle)
    // coui-ui 依赖 org.jetbrains.compose.foundation 1.11.1，本地缓存为 1.12.0，显式钉死
    implementation(libs.compose.foundation)

    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // activity-compose 1.13.0 传递依赖 navigationevent:1.0.0，本地仅有 1.1.2；显式提升避免离线解析失败
    implementation("androidx.navigationevent:navigationevent:1.1.2")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
}
