plugins { id("com.android.library"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.serialization") }
android { namespace="com.yasser.reaconsole.core.network"; compileSdk=36; defaultConfig { minSdk=26 } }
dependencies { implementation("com.squareup.retrofit2:retrofit:2.11.0"); implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0"); implementation("com.squareup.okhttp3:okhttp:4.12.0") }
