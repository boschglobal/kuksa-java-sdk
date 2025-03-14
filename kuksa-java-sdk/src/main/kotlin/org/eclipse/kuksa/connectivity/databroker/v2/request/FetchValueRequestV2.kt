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

package org.eclipse.kuksa.connectivity.databroker.v2.request

import org.eclipse.kuksa.proto.v2.Types.SignalID

/**
 * Used for fetch value requests with
 * [org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection.kuksaValV2.fetchValue].
 */
data class FetchValueRequestV2(val signalId: SignalID) {
    companion object {
        fun fromVssPath(vssPath: String): FetchValueRequestV2 {
            val signalId = SignalID.newBuilder().setPath(vssPath).build()
            return FetchValueRequestV2(signalId)
        }

        fun fromId(id: Int): FetchValueRequestV2 {
            val signalId = SignalID.newBuilder().setId(id).build()
            return FetchValueRequestV2(signalId)
        }
    }
}
