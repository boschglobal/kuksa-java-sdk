/*
 * Copyright (c) 2023 - 2025 Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

plugins {
    `maven-publish`
    signing
}

interface PublishPluginExtension {
    val artifactGroup: Property<String>
    val artifactName: Property<String>
    val artifactVersion: Property<String>
    val mavenPublicationName: Property<String>
    val componentName: Property<String>
    val description: Property<String>
}

val extension = project.extensions.create<PublishPluginExtension>("publish")

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>(extension.mavenPublicationName.get()) {
                from(components[extension.componentName.get()])
                groupId = extension.artifactGroup.getOrElse(project.group.toString())
                artifactId = extension.artifactName.getOrElse(project.name)
                version = extension.artifactVersion.getOrElse(project.version.toString())

                pom {
                    name = "${project.group}:${project.name}"
                    description = extension.description.get()
                    url = "https://github.com/eclipse-kuksa/kuksa-java-sdk"
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name = "Mark Hüsers"
                            email = "mark.huesers@etas.com"
                            organization = "ETAS GmbH"
                            organizationUrl = "https://www.etas.com"
                        }
                        developer {
                            name = "Sebastian Schildt"
                            email = "sebastian.schildt@etas.com"
                            organization = "ETAS GmbH"
                            organizationUrl = "https://www.etas.com"
                        }
                        developer {
                            name = "Andre Weber"
                            email = "andre.weber3@etas.com"
                            organization = "ETAS GmbH"
                            organizationUrl = "https://www.etas.com"
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/eclipse-kuksa/kuksa-java-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/eclipse-kuksa/kuksa-java-sdk.git")
                        url.set("https://github.com/eclipse-kuksa/kuksa-java-sdk/tree/main")
                    }
                }
            }
        }
    }

    signing {
        var keyId: String? = System.getenv("ORG_GPG_KEY_ID")
        if (keyId != null && keyId.length > 8) {
            keyId = keyId.takeLast(8)
        }
        val privateKey = System.getenv("ORG_GPG_PRIVATE_KEY")
        val passphrase = System.getenv("ORG_GPG_PASSPHRASE")

        useInMemoryPgpKeys(
            keyId,
            privateKey,
            passphrase,
        )

        sign(publishing.publications)

        setRequired({
            val publishToMavenLocalTask = gradle.taskGraph.allTasks.find { it.name.contains("ToMavenLocal") }
            val isPublishingToMavenLocal = publishToMavenLocalTask != null

            !isPublishingToMavenLocal // disable signing when publishing to MavenLocal
        })
    }
}
