plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jocude.vectorscan"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jocude.vectorscan"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        // Servidor por defecto: el ordenador anfitrión visto desde el emulador.
        // Se puede cambiar en Ajustes sin recompilar.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8000/\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.material)
    implementation(libs.okhttp)

    // Visor 3D (Filament) y realidad aumentada (ARCore).
    // SceneView 1.x mantiene una API usable desde Java; la 2.x exige Kotlin y Compose.
    implementation(libs.sceneview)
    implementation(libs.arsceneview)
    implementation(libs.arcore)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.json)
}
