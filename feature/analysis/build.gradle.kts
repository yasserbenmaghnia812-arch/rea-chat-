plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.serialization") }
android { namespace="com.yasser.reaconsole.feature.analysis"; compileSdk=36; defaultConfig { minSdk=26 } }
dependencies { implementation(project(":core:network")); implementation(project(":core:data")); implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
