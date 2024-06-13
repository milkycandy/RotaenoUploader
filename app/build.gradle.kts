plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id ("com.huawei.agconnect")
}

android {
    namespace = "cn.milkycandy.rotaenoupdater"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.milkycandy.rotaenoupdater"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.6.0 beta 1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
//    implementation ("com.huawei.agconnect:agconnect-core:1.9.1.301")
//    implementation ("com.huawei.agconnect:agconnect-crash:1.9.1.301")
//    // 1.8.0.300 以后的版本，必须手动集成华为分析SDK
//    implementation ("com.huawei.hms:hianalytics:6.9.0.301")
}
