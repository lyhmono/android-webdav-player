plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.webdavplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.webdavplayer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // 由开发者自行配置签名文件（不纳入版本库）
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // ===== 播放内核风味（§1.2 方案 C） =====
    // lite：仅 Media3/ExoPlayer；full：Media3 + libVLC 双内核（应用内可切换）
    flavorDimensions += "engine"
    productFlavors {
        create("lite") {
            dimension = "engine"
            // 仅 Media3 内核
        }
        create("full") {
            dimension = "engine"
            // Media3 + libVLC 双内核
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
        buildConfig = true
    }
    composeOptions {
        // Compose 编译器版本需与 Kotlin 1.9.22 匹配
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ===== AndroidX 核心 =====
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ===== Jetpack Compose + Material 3 =====
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ===== Navigation-Compose =====
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ===== Hilt (DI) =====
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // ===== Room =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ===== Paging 3 =====
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")

    // ===== DataStore（内核选择等轻量偏好） =====
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ===== Security-Crypto（冷启动加密凭据） =====
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ===== OkHttp（自签 SSL / 鉴权拦截 / 流式） =====
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0")

    // ===== Sardine-Android（WebDAV 客户端） =====
    implementation("com.thegrizzlylabs:sardine-android:0.10.0")

    // ===== Media3（ExoPlayer 默认内核 + 后台媒体会话） =====
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-session:1.2.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.0")

    // ===== libVLC（备选内核，仅 full 风味） =====
    fullImplementation("org.videolan.android:libvlc-all:3.6.0")

    // ===== Coil（P2 缩略图预留） =====
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ===== 冷启动优化 =====
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // ===== 协程 =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ===== 测试 =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
