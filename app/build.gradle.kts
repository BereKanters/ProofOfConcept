plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.example.proofofconcept"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.proofofconcept"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")  // Make sure both are the same version

    // Core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")  // Direct reference for JUnit
    androidTestImplementation(libs.androidx.junit)  // Instrumented testing
    androidTestImplementation(libs.espresso.core)  // Espresso alias from libs.versions.toml
    implementation("androidx.camera:camera-core:1.3.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }

    implementation("androidx.camera:camera-camera2:1.3.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }

    implementation("androidx.camera:camera-lifecycle:1.3.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }

    implementation("androidx.camera:camera-view:1.3.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }


    // ML Kit and TensorFlow Lite dependencies
    implementation("com.google.mlkit:image-labeling-custom:16.0.0")
    implementation("org.tensorflow:tensorflow-lite:2.8.0")
}
