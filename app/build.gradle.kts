plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "id.mysuite.app"; compileSdk = 35
    defaultConfig { applicationId = "id.mysuite.app"; minSdk = 24; targetSdk = 28; versionCode = 1; versionName = "1.0.0" }
}
