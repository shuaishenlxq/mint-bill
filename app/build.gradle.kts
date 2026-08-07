import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.xl.bill.mint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xl.bill.mint"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // WorkManager (keep-alive supplement)
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines / DataStore
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Glance (桌面小组件)
    implementation(libs.androidx.glance.appwidget)

    // 安全加固：SQLCipher 整库加密 + Biometric 应用锁
    implementation(libs.zetetic.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.biometric)
    // BiometricPrompt 需要 FragmentActivity；显式升级 fragment 以兼容 ActivityResult API
    implementation(libs.androidx.fragment.ktx)

    // Tests
    testImplementation(libs.junit)
    // android.jar 的 org.json 是 stub，JVM 单测需用真实实现（TransferCodec 单测）
    testImplementation("org.json:json:20240303")
    // kxml2：Android 内置 KXmlParser（XmlPullParser）的上游实现——JVM 单测与真机用同一解析器，
    // 避免 DOM 解析器实现差异导致的「JVM 绿、真机挂」（历史教训：XlsxWorkbookReader）
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    debugImplementation(libs.androidx.ui.tooling)
}
