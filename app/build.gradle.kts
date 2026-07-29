import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.webdavplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.webdavplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // ===== 签名配置 =====
    val localProps = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { Properties().apply { it.inputStream().use { s -> load(s) } } }

    val keystoreFilePath = localProps?.getProperty("keystore.file")
        ?: System.getenv("KEYSTORE_FILE")
    val keystoreStorePassword = localProps?.getProperty("keystore.storePassword")
        ?: System.getenv("KEYSTORE_STORE_PASSWORD")
    val keystoreKeyAlias = localProps?.getProperty("keystore.keyAlias")
        ?: System.getenv("KEYSTORE_KEY_ALIAS")
    val keystoreKeyPassword = localProps?.getProperty("keystore.keyPassword")
        ?: System.getenv("KEYSTORE_KEY_PASSWORD")

    val hasKeystoreConfig = keystoreFilePath != null && keystoreStorePassword != null

    signingConfigs {
        create("release") {
            if (hasKeystoreConfig) {
                storeFile = file(keystoreFilePath!!)
                storePassword = keystoreStorePassword
                keyAlias = keystoreKeyAlias ?: "webdav-player"
                keyPassword = keystoreKeyPassword ?: keystoreStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        buildConfig = true
    }
    composeOptions {
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
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ===== Jetpack Compose + Material 3 =====
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-graphics:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.material:material:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // ===== Navigation-Compose =====
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ===== Hilt (DI) =====
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ===== Room =====
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // ===== Paging 3 =====
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")

    // ===== DataStore =====
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ===== Security-Crypto =====
    implementation("androidx.security:security-crypto:1.1.0")

    // ===== OkHttp =====
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0")

    // ===== Sardine-Android（WebDAV 客户端） =====
    implementation("com.github.thegrizzlylabs:sardine-android:v0.9")

    // ===== Media3（ExoPlayer + UI + 后台媒体会话） =====
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")

    // ===== 协程 =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ===== 测试 =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}