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

import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("application")
    kotlin("jvm")
    id("org.openjfx.javafxplugin")
    id("org.eclipse.velocitas.vss-processor-plugin")
}

val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

application {
    mainClass.set("org.eclipse.kuksa.testapp.MainKt")
}

tasks.named("run") {
    notCompatibleWithConfigurationCache("JavaFX plugin accesses project state at execution time")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

vssProcessor {
    searchPath.set("$rootDir/vss")
}

dependencies {
    implementation(project(":kuksa-java-sdk"))
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.stub)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.javafx)

    testImplementation(libs.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testfx.junit5)
}
