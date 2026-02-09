// Project level build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false

    // 이 부분을 아래처럼 수정해 보세요
    id("com.google.dagger.hilt.android") version libs.versions.hilt.get() apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}