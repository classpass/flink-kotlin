import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.Duration
import java.net.URI

plugins {
    java
    kotlin("jvm") version "1.4.32" apply false
    id("org.jetbrains.dokka") version "1.4.32" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
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


    dependencies {
        testImplementation(kotlin("test-junit5"))

        testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
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
        apply(plugin = "signing")
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
                register<MavenPublication>("sonatype") {
                    from(components["java"])
                    artifact(tasks["docJar"])
                    // sonatype required pom elements
                    pom {
                        name.set("${project.group}:${project.name}")
                        description.set(name)
                        url.set("https://github.com/classpass/flink-kotlin")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.html")
                            }
                        }
                        developers {
                            developer {
                                id.set("marshallpierce")
                                name.set("Marshall Pierce")
                                email.set("575695+marshallpierce@users.noreply.github.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/classpass/flink-kotlin")
                            developerConnection.set("scm:git:https://github.com/classpass/flink-kotlin.git")
                            url.set("https://github.com/classpass/flink-kotlin")
                        }
                    }
                }
            }

            // A safe throw-away place to publish to:
            // ./gradlew publishSonatypePublicationToLocalDebugRepository -Pversion=foo
            repositories {
                maven {
                    name = "localDebug"
                    url = URI.create("file:///${project.buildDir}/repos/localDebug")
                }
            }
        }

        // don't barf for devs without signing set up
        if (project.hasProperty("signing.keyId")) {
            configure<SigningExtension> {
                sign(project.extensions.getByType<PublishingExtension>().publications["sonatype"])
            }
        }

        // releasing should publish
        rootProject.tasks.afterReleaseBuild {
            dependsOn(provider { project.tasks.named("publishToSonatype") })
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            // sonatypeUsername and sonatypePassword properties are used automatically
            stagingProfileId.set("1f02cf06b7d4cd") // com.classpass.oss
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
    // these are not strictly required. The default timeouts are set to 1 minute. But Sonatype can be really slow.
    // If you get the error "java.net.SocketTimeoutException: timeout", these lines will help.
    connectTimeout.set(Duration.ofMinutes(3))
    clientTimeout.set(Duration.ofMinutes(3))
}

release {
    // work around lack of proper kotlin DSL support
    (getProperty("git") as net.researchgate.release.GitAdapter.GitConfig).apply {
        requireBranch = "main"
    }
}
