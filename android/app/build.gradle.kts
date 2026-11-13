plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.cleanmytv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.cleanmytv.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.1"
    }

    signingConfigs {
        // Ключ лежит в репозитории намеренно: приложение обновляет себя APK из релиза,
        // а Android ставит обновление только поверх сборки с той же подписью.
        create("stable") {
            storeFile = file("../keystore/cleanmytv.jks")
            storePassword = "cleanmytv"
            keyAlias = "cleanmytv"
            keyPassword = "cleanmytv"
        }
    }

    buildTypes {
        debug {
            // Один и тот же ключ у локальной сборки и у CI: только так APK из релиза
            // может обновиться поверх установленного приложения.
            signingConfig = signingConfigs.getByName("stable")
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("stable")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("dev.mobile:dadb:2.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
