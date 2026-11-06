package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.ProjectTemplate
import java.io.File

/**
 * Menghasilkan berkas awal sesuai template. Kerangka ANDROID_KOTLIN adalah struktur proyek Gradle
 * standar; kemampuannya dibangun menjadi APK baru diuji pada Tahap 6 (belum ada Gradle Wrapper di sini).
 */
internal object ProjectTemplates {

    fun generate(dir: File, name: String, packageName: String, template: ProjectTemplate) {
        when (template) {
            ProjectTemplate.EMPTY -> generateEmpty(dir, name)
            ProjectTemplate.ANDROID_KOTLIN -> generateAndroidKotlin(dir, name, packageName)
        }
    }

    private fun generateEmpty(dir: File, name: String) {
        write(dir, "README.md", "# $name\n\nDibuat dengan Create App.\n")
    }

    private fun generateAndroidKotlin(dir: File, name: String, packageName: String) {
        val gradleName = kotlinStringEscape(name)
        write(
            dir,
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "$gradleName"
            include(":app")
            """.trimIndent() + "\n",
        )
        write(
            dir,
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "8.10.1" apply false
                id("org.jetbrains.kotlin.android") version "2.1.21" apply false
            }
            """.trimIndent() + "\n",
        )
        write(
            dir,
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            """.trimIndent() + "\n",
        )
        write(
            dir,
            ".gitignore",
            """
            .gradle/
            build/
            local.properties
            *.jks
            *.keystore
            keystore.properties
            """.trimIndent() + "\n",
        )
        write(
            dir,
            "app/build.gradle.kts",
            """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "$packageName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 26
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            kotlin {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
            """.trimIndent() + "\n",
        )
        write(
            dir,
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <application
                    android:allowBackup="false"
                    android:label="@string/app_name"
                    android:theme="@android:style/Theme.Material.Light.NoActionBar">

                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>

                </application>

            </manifest>
            """.trimIndent() + "\n",
        )
        write(
            dir,
            "app/src/main/res/values/strings.xml",
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">${xmlStringEscape(name)}</string>\n" +
                "</resources>\n",
        )
        val packagePath = packageName.replace('.', '/')
        write(
            dir,
            "app/src/main/kotlin/$packagePath/MainActivity.kt",
            """
            package $packageName

            import android.app.Activity
            import android.os.Bundle
            import android.widget.TextView

            class MainActivity : Activity() {

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val label = TextView(this)
                    label.text = getString(R.string.app_name)
                    setContentView(label)
                }
            }
            """.trimIndent() + "\n",
        )
    }

    private fun write(dir: File, relativePath: String, content: String) {
        atomicWrite(File(dir, relativePath), content.toByteArray(Charsets.UTF_8))
    }

    /** Escape untuk string literal Kotlin/Gradle: `$` harus di-escape agar tidak dianggap template. */
    internal fun kotlinStringEscape(text: String): String = buildString {
        for (c in text) {
            if (c == '$') append("\\$") else append(c)
        }
    }

    /** Escape untuk isi `<string>` di resource Android: karakter XML, tanda kutip, dan `@`/`?` di awal. */
    internal fun xmlStringEscape(text: String): String {
        val builder = StringBuilder()
        text.forEachIndexed { index, c ->
            when (c) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '\'' -> builder.append("\\'")
                '"' -> builder.append("\\\"")
                '@', '?' -> {
                    if (index == 0) builder.append('\\')
                    builder.append(c)
                }
                else -> builder.append(c)
            }
        }
        return builder.toString()
    }
}
