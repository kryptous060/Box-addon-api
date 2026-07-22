plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  id("com.chaquo.python")
}

android {
  namespace = "com.google.ai.edge.gallery.addon"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.box.gallery.addon"
    minSdk = 35
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }

    python {
        pip {
            install("fastapi", "uvicorn", "starlette", "pydantic")
        }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)

  // Link core engine libraries
  implementation(project(":smollm"))
  implementation(project(":stablediffusion"))
  implementation(project(":whisper"))
}
