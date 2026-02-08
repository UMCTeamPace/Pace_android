import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.pace"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pace"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        manifestPlaceholders["GOOGLE_API_KEY"] = "${properties.getProperty("GOOGLE_API_KEY")}"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures{
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.rfc5545.datetime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Modal Case Indicator
    implementation("me.relex:circleindicator:2.1.6")

    //캘린더뷰 라이브러리
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.kizitonwose.calendar:view:2.5.1")
    implementation("com.kizitonwose.calendar:core:2.5.1")

    implementation(libs.biweekly)


    //지도 sdk
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    //내 위치
    implementation("com.google.android.gms:play-services-location:21.0.1")
    //구글 place api
    implementation("com.google.android.libraries.places:places:3.5.0")
    // Coroutines (디바운싱 - 시간 지연용) 장소 검색
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    //Material Dialogs 라이브러리 주석 처리
    implementation("com.afollestad.material-dialogs:core:3.3.0")
    implementation("com.afollestad.material-dialogs:color:3.3.0")

    //viewpager2 최신 안정화 버전
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    //tablayout 라이브러리
    implementation("com.google.android.material:material:1.11.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    //splash
    implementation("com.airbnb.android:lottie:6.4.0")

    // Dots Indicator
    implementation("com.tbuonomo:dotsindicator:4.3")

    //Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")

    // Retrofit 라이브러리
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // JSON 변환을 위한 Gson 컨버터
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}