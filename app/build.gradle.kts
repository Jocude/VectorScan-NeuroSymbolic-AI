import java.net.NetworkInterface

plugins {
    id("com.android.application")
}

fun getLocalIp(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces.asSequence()) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in iface.inetAddresses.asSequence()) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                        return ip
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return "10.0.2.2" // Default for emulator to host
}

android {
    namespace = "com.example.vectorscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vectorscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val localIp = getLocalIp()
        buildConfigField("String", "BASE_URL", "\"http://$localIp:8000/\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Versiones actualizadas a las más recientes estables compatibles
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Retrofit y OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Commons IO
    implementation("commons-io:commons-io:2.18.0")

    // SceneView y ARCore
    // Mantenemos 1.2.6 para SceneView porque la 2.x+ requiere migración a Kotlin/Compose
    implementation("io.github.sceneview:sceneview:1.2.6")
    implementation("com.google.ar:core:1.47.0")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
