import com.jfrog.bintray.gradle.BintrayExtension
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

plugins {
    java
    kotlin("jvm") version "1.4.10" apply false
    id("com.jfrog.bintray") version "1.8.5" apply false
    id("org.jetbrains.dokka") version "1.4.0" apply false
    id("net.researchgate.release") version "2.8.1"
    id("com.github.ben-manes.versions") version "0.33.0"
    id("org.jmailen.kotlinter") version "3.2.0"
    id("com.github.hierynomus.license") version "0.16.1" apply false
}

subprojects {
    // until plugins{} block is available in subprojects, do it the hard way
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "com.github.hierynomus.license")

    repositories {
        mavenCentral()
    }

    val deps by extra {
        mapOf(
            "flink" to "1.11.2",
            "junit" to "5.7.0",
            "slf4j" to "1.7.30"
        )
    }

    dependencies {
        testImplementation(kotlin("test-junit5"))

        testImplementation("org.junit.jupiter:junit-jupiter:${deps["junit"]}")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${deps["junit"]}")
    }

    java {
        withSourcesJar()
    }

    configure<nl.javadude.gradle.plugins.license.LicenseExtension> {
        header = rootProject.file("LICENSE-header")
    }

    tasks {
        // enable ${year} substitution
        OffsetDateTime.now(ZoneOffset.UTC).let { now ->
            withType<com.hierynomus.gradle.license.tasks.LicenseFormat> {
                extra["year"] = now.year.toString()
            }
            withType<com.hierynomus.gradle.license.tasks.LicenseCheck> {
                extra["year"] = now.year.toString()
            }
        }

        test {
            useJUnitPlatform()
        }

        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

    }

}

// only publish certain projects
subprojects.filter { listOf("flink-core-kotlin", "flink-streaming-kotlin").contains(it.name) }.forEach { project ->
    project.run {
        apply(plugin = "maven-publish")
        apply(plugin = "com.jfrog.bintray")
        apply(plugin = "org.jetbrains.dokka")

        group = "com.classpass.oss.flink.kotlin"

        tasks {
            register<Jar>("docJar") {
                from(project.tasks["dokkaHtml"])
                archiveClassifier.set("javadoc")
            }
        }

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("bintray") {
                    from(components["java"])
                    artifact(tasks["docJar"])
                }
            }
        }

        configure<BintrayExtension> {
            user = rootProject.findProperty("bintrayUser")?.toString()
            key = rootProject.findProperty("bintrayApiKey")?.toString()
            setPublications("bintray")

            with(pkg) {
                userOrg = "classpass-oss"
                repo = "maven"
                setLicenses("Apache-2.0")
                vcsUrl = "https://github.com/classpass/flink-kotlin"
                // can use one Bintray package because these all have the same group id
                name = "flink-kotlin"

                with(version) {
                    name = project.version.toString()
                    released = Instant.now().toString()
                    vcsTag = project.version.toString()
                }
            }
        }
    }
}

tasks {
    afterReleaseBuild {
        dependsOn(named("bintrayUpload"))
    }
}
