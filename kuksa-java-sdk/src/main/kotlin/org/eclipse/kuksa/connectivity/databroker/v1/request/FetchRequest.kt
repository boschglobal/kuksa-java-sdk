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

package org.eclipse.kuksa.connectivity.databroker.v1.request

import org.eclipse.kuksa.proto.v1.Types

/**
 * Used for fetch requests with [org.eclipse.kuksa.connectivity.databroker.v1.DataBrokerConnection.fetch].
 */
open class FetchRequest @JvmOverloads constructor(
    override val vssPath: String,
    override vararg val fields: Types.Field = arrayOf(Types.Field.FIELD_VALUE),
) : DataBrokerRequest
