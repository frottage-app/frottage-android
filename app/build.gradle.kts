plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
}

android {
    namespace = "com.frottage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frottage"
        minSdk = 21 // Android 5 - https://apilevels.com
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Required when setting minSdkVersion to 20 or lower
        multiDexEnabled = true

        if (project.hasProperty("VERSION_CODE")) {
            versionCode = (project.property("VERSION_CODE") as String).toInt()
        }
        if (project.hasProperty("VERSION_NAME")) {
            versionName = project.property("VERSION_NAME") as String
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keys/keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = "my-alias"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.20"
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        // Sets Java compatibility to Java 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt.coil3:coil-core:3.2.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-cache-control:3.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.google.android.play:review-ktx:2.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

base {
    archivesName.set("frottage")
}

tasks.withType<Test> {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
