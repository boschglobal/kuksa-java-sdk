/*
 * Copyright (c) 2023 - 2026 Contributors to the Eclipse Foundation
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

pluginManagement {

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }

    // Version catalog can't be used here
    plugins {
        id("com.google.devtools.ksp") version "2.3.11"
        id("org.openjfx.javafxplugin") version "0.1.0"
        id("org.eclipse.velocitas.vss-processor-plugin") version "0.1.3"
        kotlin("jvm")
        kotlin("plugin.serialization") version "2.4.10"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "kuksa-java-sdk"

include(":kuksa-java-sdk")
include(":vss-core")
include(":test-core")
include(":samples")
include(":mock-provider")
if (System.getenv("DOCKER_BUILD") != "true") {
    include(":kuksa-java-testapp")
}
