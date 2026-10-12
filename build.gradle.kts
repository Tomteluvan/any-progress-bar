import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.anyprogressbar"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("com.github.weisj:jsvg:1.6.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }
// Local-only artwork (src/main/resources/art/brand) is git-ignored; pass -PpublicBuild to leave it out of the plugin.
tasks.processResources { if (project.hasProperty("publicBuild")) exclude("art/brand/**") }
tasks.test { useJUnitPlatform(); systemProperty("java.awt.headless", "true") }

intellijPlatform {
    pluginConfiguration {
        name = "Any Progress Bar"
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
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
