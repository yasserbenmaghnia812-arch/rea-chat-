plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization"); id("com.google.dagger.hilt.android"); id("com.google.devtools.ksp")
}
android {
 namespace = "com.yasser.reaconsole"; compileSdk = 36
 defaultConfig { applicationId = "com.yasser.reaconsole"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "1.0.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(project(":core:network")); implementation(project(":core:data")); implementation(project(":feature:analysis"))
 implementation(platform("androidx.compose:compose-bom:2025.10.00"))
 implementation("androidx.activity:activity-compose:1.11.0"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4"); implementation("androidx.datastore:datastore-preferences:1.1.7")
 implementation("androidx.security:security-crypto:1.1.0"); implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"); implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.google.dagger:hilt-android:2.57.2"); ksp("com.google.dagger:hilt-compiler:2.57.2")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
