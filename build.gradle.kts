import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.intellij.changelog)
}

group = "dev.anyprogressbar"
version = "1.4.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
        testFramework(TestFrameworkType.Platform)
    }
    implementation(libs.jsvg)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }
// Local-only artwork (src/main/resources/art/brand) is git-ignored and left out of the plugin by default;
// pass -PlocalBuild to include it in builds for your own use.
tasks.processResources { if (!project.hasProperty("localBuild")) exclude("art/brand/**") }
tasks.test { useJUnitPlatform(); systemProperty("java.awt.headless", "true") }

changelog {
    path = file("CHANGELOG.md").absolutePath
    groups = emptyList() // CHANGELOG.md uses plain bullet lists, not Added/Fixed groups
}

intellijPlatform {
    pluginConfiguration {
        name = "Any Progress Bar"
        // Marketplace change notes come from the CHANGELOG.md section matching the project version
        changeNotes = provider {
            changelog.renderItem(changelog.get(project.version.toString()), Changelog.OutputType.HTML)
        }
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides { recommended() }
    }
}

// CI publishes the archive its build job already verified: pass -PpluginArchive=<zip> (with -x buildPlugin) to upload
// that exact file instead of rebuilding it.
providers.gradleProperty("pluginArchive").orNull?.let { verified ->
    tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask>("publishPlugin") {
        archiveFiles.setFrom(layout.projectDirectory.file(verified))
    }
}

val pluginArchive = tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }
val pluginJarName = "/${project.name}-${project.version}.jar"

tasks.register("verifyCompatibilityMetadata") {
    val archiveFile = pluginArchive
    val expectedJarName = pluginJarName
    group = "verification"
    description = "Checks the packaged plugin supports build 261 and has no upper build limit."
    dependsOn("buildPlugin")
    inputs.file(archiveFile)
    doLast {
        ZipFile(archiveFile.get().asFile).use { archive ->
            val jar = archive.entries().asSequence().single { it.name.endsWith(expectedJarName) }
            ZipInputStream(archive.getInputStream(jar)).use { contents ->
                check(generateSequence { contents.nextEntry }.any { it.name == "META-INF/plugin.xml" }) {
                    "The plugin JAR must contain META-INF/plugin.xml"
                }
                val factory = DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                val descriptor = factory.newDocumentBuilder().parse(contents)
                val ideaVersion = descriptor.getElementsByTagName("idea-version").item(0) as org.w3c.dom.Element
                check(ideaVersion.getAttribute("since-build") == "261") { "Minimum build must be 261" }
                check(!ideaVersion.hasAttribute("until-build")) { "Future builds must not be capped" }
                this.logger.lifecycle("Packaged compatibility: since-build=261, no until-build")
            }
        }
    }
}
