plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android { namespace="com.yasser.reaconsole.core.data"; compileSdk=36; defaultConfig { minSdk=26 } }
dependencies { implementation("androidx.datastore:datastore-preferences:1.1.7"); implementation("androidx.security:security-crypto:1.1.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }
