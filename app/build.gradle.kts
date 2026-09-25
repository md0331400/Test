import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Release signing is developer-held: create `release.properties` in the project root with
//   storeFile=kothabolbo-release.jks
//   storePassword=...
//   keyAlias=kothabolbo
//   keyPassword=...
// and place the keystore next to it. NEITHER file is ever committed (see .gitignore).
// When release.properties is absent the release build still compiles but stays UNSIGNED,
// so CI/verification builds never fail on a missing secret.
val releasePropertiesFile = rootProject.file("release.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.exists()) FileInputStream(releasePropertiesFile).use { load(it) }
}
val hasReleaseSigning = releasePropertiesFile.exists() &&
    releaseProperties.getProperty("storeFile") != null &&
    rootProject.file(releaseProperties.getProperty("storeFile")).exists()
if (!hasReleaseSigning) {
    logger.warn("Kotha Bolbo: release.properties / keystore not found — release build will be UNSIGNED. See docs/BUILD-RELEASE.md")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.amisayem.kothabolbo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.amisayem.kothabolbo"
        minSdk = 26
        targetSdk = 36
        versionCode = 40
        versionName = "1.0.40"
        // Client-side ImageKit public key (not a secret; the private key stays on the Vercel backend).
        buildConfigField("String", "IMAGEKIT_PUBLIC_KEY", "\"public_o5b4VUN6FacV5cteyv\"")
        // Google OAuth *web* client id used for Firebase Google sign-in id tokens.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"343828860232-n7korusids9jcse961go8tqne8abob59.apps.googleusercontent.com\""
        )
        buildConfigField("String", "API_BASE_URL", "\"https://kothabolbo.vercel.app\"")
        buildConfigField("String", "IMAGEKIT_CDN_BASE", "\"https://ik.imagekit.io/sayemgogo/\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
