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

package org.eclipse.kuksa.connectivity.databroker.v2.extensions

import io.kotest.assertions.fail
import org.eclipse.kuksa.connectivity.databroker.v2.DataBrokerInvokerV2
import org.eclipse.kuksa.proto.v2.Types
import org.eclipse.kuksa.proto.v2.Types.SignalID
import kotlin.random.Random

internal suspend fun DataBrokerInvokerV2.updateRandomFloatValue(
    vssPath: String,
    maxValue: Int = 300,
): Float {
    val random = Random(System.nanoTime())
    val randomValue = random.nextInt(maxValue)
    val randomFloat = randomValue.toFloat()

    val signalID = SignalID.newBuilder().setPath(vssPath).build()
    val value = Types.Value.newBuilder().setFloat(randomFloat).build()
    val updatedDatapoint = Types.Datapoint.newBuilder().setValue(value).build()

    try {
        publishValue(signalID, updatedDatapoint)
    } catch (e: Exception) {
        fail("Updating $vssPath to $randomFloat failed: $e")
    }

    return randomFloat
}

internal suspend fun DataBrokerInvokerV2.updateRandomUint32Value(
    vssPath: String,
    maxValue: Int = 300,
): Int {
    val random = Random(System.nanoTime())
    val randomValue = random.nextInt(maxValue)

    val signalID = SignalID.newBuilder().setPath(vssPath).build()
    val value = Types.Value.newBuilder().setUint32(randomValue).build()
    val updatedDatapoint = Types.Datapoint.newBuilder().setValue(value).build()

    try {
        publishValue(signalID, updatedDatapoint)
    } catch (e: Exception) {
        fail("Updating $vssPath to $randomValue failed: $e")
    }

    return randomValue
}

internal suspend fun DataBrokerInvokerV2.toggleBoolean(vssPath: String): Boolean {
    var newBoolean: Boolean? = null
    try {
        val signalId = SignalID.newBuilder().setPath(vssPath).build()
        val response = fetchValue(signalId)

        val currentBool = response.dataPoint.value.bool

        newBoolean = !currentBool
        val value = Types.Value.newBuilder().setBool(newBoolean).build()
        val newDatapoint = Types.Datapoint.newBuilder().setValue(value).build()

        publishValue(signalId, newDatapoint)
    } catch (e: Exception) {
        fail("Updating $vssPath to $newBoolean failed: $e")
    }

    return newBoolean == true
}
