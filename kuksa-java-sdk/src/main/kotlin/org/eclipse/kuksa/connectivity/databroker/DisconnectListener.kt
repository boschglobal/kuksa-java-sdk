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

package org.eclipse.kuksa.connectivity.databroker

import org.eclipse.kuksa.pattern.listener.Listener

/**
 * The [DisconnectListener] can be registered to
 * [org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection.disconnectListeners]
 * When registered it will notify about manual or unexpected connection disconnects from the DataBroker.
 */
fun interface DisconnectListener : Listener {
    /**
     * Will be triggered, when the connection to the DataBroker was closed manually or unexpectedly.
     */
    fun onDisconnect()
}
