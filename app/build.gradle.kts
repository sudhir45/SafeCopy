import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sudhir.safecopy"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sudhir.safecopy"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Spec 14.4: the release key is read from ~/.gradle/gradle.properties or environment
    // variables, never from this repo. Without them, release builds come out unsigned.
    val keystorePath = providers.gradleProperty("SAFECOPY_KEYSTORE").orElse(providers.environmentVariable("SAFECOPY_KEYSTORE")).orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("SAFECOPY_KEYSTORE_PASSWORD")
                    .orElse(providers.environmentVariable("SAFECOPY_KEYSTORE_PASSWORD")).get()
                keyAlias = providers.gradleProperty("SAFECOPY_KEY_ALIAS")
                    .orElse(providers.environmentVariable("SAFECOPY_KEY_ALIAS")).get()
                keyPassword = providers.gradleProperty("SAFECOPY_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("SAFECOPY_KEY_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        debug {
            val secure = providers.gradleProperty("safecopy.debugSecure").orNull.toBoolean()
            buildConfigField("boolean", "FORCE_SECURE", secure.toString())
        }
        release {
            buildConfigField("boolean", "FORCE_SECURE", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// --- Permission guard (spec 2 and 14.3) ---------------------------------------------------
// Fails the build if the merged manifest requests any permission that breaks the privacy
// invariants. Wired into every assemble<Variant> task.

abstract class CheckForbiddenPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val forbidden = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        )
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map {
                (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name")
            }
        }.sorted()

        report.get().asFile.writeText(requested.joinToString("\n", postfix = "\n"))

        val bad = requested.filter { it in forbidden }
        if (bad.isNotEmpty()) {
            throw GradleException(
                "Forbidden permissions in merged manifest: ${bad.joinToString()}.\n" +
                    "SafeCopy must not request network, camera or storage permissions (spec section 2). " +
                    "Strip them with tools:node=\"remove\" in AndroidManifest.xml.",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val guard = tasks.register<CheckForbiddenPermissions>("check${cap}Permissions") {
            group = "verification"
            description = "Fails if the ${variant.name} merged manifest requests forbidden permissions."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        tasks.matching { it.name == "assemble$cap" || it.name == "bundle$cap" }.configureEach { dependsOn(guard) }
    }
}
