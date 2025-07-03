apply plugin: 'com.android.application'
apply plugin: 'org.jetbrains.kotlin.android'
apply plugin: 'kotlin-kapt'
apply plugin: 'com.google.dagger.hilt.android'
apply plugin: 'com.google.gms.google-services'
apply plugin: 'org.jetbrains.kotlin.plugin.serialization'

android {
    namespace 'com.messagehub'
    compileSdk 34

    defaultConfig {
        applicationId "com.messagehub"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary true
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    buildFeatures {
        compose true
    }

    composeOptions {
        kotlinCompilerExtensionVersion '1.5.8'
    }

    packagingOptions {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
}

// ✅ Dependencies are now wrapped in parentheses to ensure correct parsing
dependencies {
    implementation('androidx.core:core-ktx:1.12.0')
    implementation('androidx.lifecycle:lifecycle-runtime-ktx:2.7.0')
    implementation('androidx.activity:activity-compose:1.8.2')
    implementation('androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0')

    def composeBom = platform('androidx.compose:compose-bom:2024.02.01')
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation('androidx.compose.ui:ui')
    implementation('androidx.compose.ui:ui-graphics')
    implementation('androidx.compose.ui:ui-tooling-preview')
    implementation('androidx.compose.material3:material3')
    implementation('androidx.compose.animation:animation')

    implementation('com.google.android.material:material:1.12.0')

    implementation('com.google.dagger:hilt-android:2.50')
    kapt('com.google.dagger:hilt-compiler:2.50')
    implementation('androidx.hilt:hilt-work:1.2.0')
    kapt('androidx.hilt:hilt-compiler:1.2.0')

    implementation('androidx.work:work-runtime-ktx:2.9.0')

    implementation('com.squareup.retrofit2:retrofit:2.9.0')
    implementation('com.squareup.retrofit2:converter-gson:2.9.0')
    implementation('com.squareup.okhttp3:logging-interceptor:4.12.0')

    implementation('org.nanohttpd:nanohttpd:2.3.1')
    implementation('io.coil-kt:coil-compose:2.6.0')
    implementation('androidx.documentfile:documentfile:1.0.1')

    implementation('androidx.emoji2:emoji2:1.4.0')
    implementation('androidx.emoji2:emoji2-views:1.4.0')
    implementation('androidx.emoji2:emoji2-views-helper:1.4.0')

    implementation(platform('com.google.firebase:firebase-bom:32.7.4'))
    implementation('com.google.firebase:firebase-messaging-ktx')
    implementation('com.google.firebase:firebase-analytics-ktx')

    implementation('com.google.mlkit:language-id:17.0.5')
    implementation('com.google.mlkit:translate:17.0.2')

    implementation('org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3')

    testImplementation('junit:junit:4.13.2')
    androidTestImplementation('androidx.test.ext:junit:1.1.5')
    androidTestImplementation('androidx.test.espresso:espresso-core:3.5.1')
    androidTestImplementation('androidx.compose:ui-test-junit4')
    debugImplementation('androidx.compose.ui:ui-tooling')
    debugImplementation('androidx.compose.ui:ui-test-manifest')
}
