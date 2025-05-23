plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.frottage"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.frottage"
        minSdk = 21 // Android 5 - https://apilevels.com
        targetSdk = 34
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
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        // Sets Java compatibility to Java 8
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    kotlin {
        jvmToolchain(1_8)
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
    implementation("io.coil-kt:coil-compose:2.7.0") // coil3.0.3 was caching too much
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
