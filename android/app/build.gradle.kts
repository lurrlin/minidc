plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.unme"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.unme"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.stream.webrtc.android)
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
}