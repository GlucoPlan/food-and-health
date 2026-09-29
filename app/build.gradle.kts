import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// ─── Версия читается из version.properties ────────────────────────────────────
// VERSION_MAJOR и VERSION_MINOR меняются вручную,
// VERSION_PATCH увеличивает GitHub Actions при каждом push в main.
val versionProps = Properties().also { props ->
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}
val vMajor = versionProps.getProperty("VERSION_MAJOR", "0").trim().toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR", "1").trim().toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH", "0").trim().toInt()
val appVersionName = "$vMajor.$vMinor.$vPatch"
// Запас по 1000 на MINOR и PATCH: патч растёт с каждым push
val appVersionCode = vMajor * 1_000_000 + vMinor * 1_000 + vPatch

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glucoplan.foodhealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.glucoplan.foodhealth"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Репозиторий, где лежат релизы (проверка обновлений)
        buildConfigField("String", "GITHUB_REPO", "\"GlucoPlan/food-and-health\"")

        // Только ARM: на них работают телефоны. Модель ML Kit для x86 лишь раздула бы APK,
        // который скачивается при каждом обновлении
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric: тесты базы и Android-классов на JVM, без эмулятора
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    // Схемы Room хранятся в репозитории: по ним пишутся тесты миграций
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Сканер штрихкода: камера и распознавание без интернета (модель внутри APK)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
