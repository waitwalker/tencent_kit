import java.util.regex.Pattern

group = "io.github.v7lin.tencent_kit"

plugins {
    id("com.android.library")
}

val packagePubspec = project.projectDir.parentFile.resolve("pubspec.yaml")
val packageYaml = packagePubspec.readText()
val libraryVersion =
    Pattern.compile("^version:\\s*['|\"]?([^\\n|'|\"]*)['|\"]?$", Pattern.MULTILINE)
        .matcher(packageYaml)
        .let {
            check(it.find()) { "version not found in pubspec.yaml" }
            it.group(1).replace("+", "-")
        }
version = libraryVersion

val hostPubspec = rootProject.projectDir.parentFile.resolve("pubspec.yaml")
val hostYaml = if (hostPubspec.exists()) hostPubspec.readText() else ""
val appId =
    Pattern.compile("(?s)tencent_kit:\\s*(?:\\n\\s+[^\\n]*)*?\\n\\s+app_id:\\s*['|\"]?([^\\n'|\"]+)['|\"]?")
        .matcher(hostYaml)
        .let {
            if (it.find()) it.group(1).trim() else null
        }
        ?: throw IllegalArgumentException(
            "tencent app id is null, add code in pubspec.yaml:\n" +
                "tencent_kit:\n" +
                "  app_id: \${your tencent app id}\n" +
                "  universal_link: https://\${your applinks domain}/universal_link/\${example_app}/qq_conn/\${your tencent app id}/ # 可选项目"
        )

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

android {
    namespace = "io.github.v7lin.tencent_kit"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    resourcePrefix = "tencent_kit"

    defaultConfig {
        minSdk = 16
        consumerProguardFiles("consumer-rules.pro")
        manifestPlaceholders["TENCENT_APP_ID"] = appId
    }

    flavorDimensions += "vendor"

    productFlavors {
        create("vendor") {
            dimension = "vendor"
            consumerProguardFiles("consumer-vendor-rules.pro")
        }
    }

    dependencies {
        add("vendorImplementation", fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))
        testImplementation("junit:junit:4.13.2")
        testImplementation("org.mockito:mockito-core:5.0.0")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.outputs.upToDateWhen { false }
                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}
