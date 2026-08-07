plugins {
    alias(libs.plugins.android.application)
}

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val firebaseApiKey = providers.gradleProperty("CALLSECURE_FIREBASE_API_KEY").orNull.orEmpty()
val firebaseAppId = providers.gradleProperty("CALLSECURE_FIREBASE_APP_ID").orNull.orEmpty()
val firebaseProjectId = providers.gradleProperty("CALLSECURE_FIREBASE_PROJECT_ID").orNull.orEmpty()
val firebaseAppCheckEnabled = providers.gradleProperty("CALLSECURE_FIREBASE_APP_CHECK_ENABLED")
    .orNull
    ?.equals("true", ignoreCase = true)
    ?: false

android {
    namespace = "com.tridev.callsecurepro"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tridev.callsecurepro"

        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "FIREBASE_API_KEY", quotedBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APP_ID", quotedBuildConfig(firebaseAppId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quotedBuildConfig(firebaseProjectId))
        buildConfigField("boolean", "FIREBASE_APP_CHECK_ENABLED", firebaseAppCheckEnabled.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

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
}

dependencies {
    // Core Android UI
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)

    // Offline phone-number parsing, formatting and validation metadata
    implementation(libs.libphonenumber)

    // Lifecycle and architecture
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.process)

    // Room local database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Reliable background work
    implementation(libs.work.runtime)

    // Firebase community + authenticated multi-source intelligence backend.
    // Configuration comes from local Gradle properties, never source.
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Local unit tests
    testImplementation(libs.junit)

    // Android instrumented tests
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
