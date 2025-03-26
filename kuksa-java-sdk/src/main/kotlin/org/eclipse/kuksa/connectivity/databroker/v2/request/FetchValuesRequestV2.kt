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
 * Used for fetch values requests with
 * [org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection.kuksaValV2.fetchValues].
 */
data class FetchValuesRequestV2(val signalIds: List<SignalID>) {
    companion object {
        fun fromVssPaths(vssPaths: List<String>): FetchValuesRequestV2 {
            val signalIds = vssPaths.map { vssPath -> SignalID.newBuilder().setPath(vssPath).build() }
            return FetchValuesRequestV2(signalIds)
        }

        fun fromIds(ids: List<Int>): FetchValuesRequestV2 {
            val signalIds = ids.map { id -> SignalID.newBuilder().setId(id).build() }
            return FetchValuesRequestV2(signalIds)
        }
    }
}
