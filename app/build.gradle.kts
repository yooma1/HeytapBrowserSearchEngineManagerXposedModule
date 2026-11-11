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
        versionCode = 22
        versionName = "2.0.2-coui"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
