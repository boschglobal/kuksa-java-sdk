/*
 * Copyright (c) 2023 - 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.kuksa.connectivity.databroker.docker

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import org.eclipse.kuksa.test.TestResourceFile

const val DEFAULT_PORT_SECURE = 55580

// tls enabled, authentication enabled
class SecureDataBrokerDockerContainer(
    containerName: String = "databroker_secure_unit_test",
    port: Int = DEFAULT_PORT_SECURE,
) : DataBrokerDockerContainer(containerName, port) {

    private val authenticationFolder = TestResourceFile("authentication").toString()
    private val authenticationMount = "/resources/authentication"

    private val tlsFolder = TestResourceFile("tls").toString()
    private val tlsMount = "/resources/tls"

    override val hostConfig: HostConfig = super.hostConfig
        .withBinds(
            Bind(tlsFolder, Volume(tlsMount), AccessMode.ro),
            Bind(authenticationFolder, Volume(authenticationMount), AccessMode.ro),
        )

    @Suppress("ArgumentListWrapping", "ktlint:standard:argument-list-wrapping") // better key-value pair readability
    override fun createContainer(tag: String): CreateContainerResponse {
        val withCmd = dockerClient.createContainerCmd("$repository:$tag")
            .withName(containerName)
            .withHostConfig(hostConfig)
            .withCmd(
                "--port", "$port",
                "--tls-cert", "$tlsMount/Server.pem",
                "--tls-private-key", "$tlsMount/Server.key",
                "--jwt-public-key", "$authenticationMount/jwt.key.pub",
            )
        val toString = withCmd.toString()
        return withCmd
            .exec()
    }
}
