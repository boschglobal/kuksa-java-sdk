/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
    }

    // Version catalog can't be used here
    plugins {
        id("com.google.devtools.ksp") version "1.9.22-1.0.17"
        kotlin("jvm")
        kotlin("plugin.serialization") version "1.9.22"
    }
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
