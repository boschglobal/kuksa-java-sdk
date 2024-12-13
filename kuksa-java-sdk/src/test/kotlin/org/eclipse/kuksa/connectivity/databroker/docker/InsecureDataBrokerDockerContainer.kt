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

const val DEFAULT_PORT_INSECURE = 55570

// no tls, no authentication
class InsecureDataBrokerDockerContainer(
    containerName: String = "databroker_insecure_unit_test",
    port: Int = DEFAULT_PORT_INSECURE,
) : DataBrokerDockerContainer(containerName, port) {

    @Suppress("ArgumentListWrapping", "ktlint:standard:argument-list-wrapping") // better key-value pair readability
    override fun createContainer(tag: String): CreateContainerResponse {
        return dockerClient.createContainerCmd("$repository:$tag")
            .withName(containerName)
            .withHostConfig(hostConfig)
            .withCmd(
                "--port", "$port",
                "--insecure",
            )
            .exec()
    }
}
