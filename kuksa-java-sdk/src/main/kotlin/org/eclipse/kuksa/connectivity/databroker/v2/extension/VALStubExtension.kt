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

package org.eclipse.kuksa.connectivity.databroker.v2.extension

import com.google.common.net.HttpHeaders
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import org.eclipse.kuksa.connectivity.authentication.JsonWebToken
import org.eclipse.kuksa.proto.v2.VALGrpc
import org.eclipse.kuksa.proto.v2.VALGrpcKt

internal fun VALGrpcKt.VALCoroutineStub.withAuthenticationInterceptor(
    jsonWebToken: JsonWebToken?,
): VALGrpcKt.VALCoroutineStub {
    if (jsonWebToken == null) return this

    val authenticationInterceptor = clientInterceptor(jsonWebToken)
    return withInterceptors(authenticationInterceptor)
}

internal fun VALGrpc.VALStub.withAuthenticationInterceptor(
    jsonWebToken: JsonWebToken?,
): VALGrpc.VALStub {
    if (jsonWebToken == null) return this

    val authenticationInterceptor = clientInterceptor(jsonWebToken)
    return withInterceptors(authenticationInterceptor)
}

private fun clientInterceptor(jsonWebToken: JsonWebToken): ClientInterceptor? {
    val authorizationHeader = Metadata.Key.of(HttpHeaders.AUTHORIZATION, Metadata.ASCII_STRING_MARSHALLER)

    val metadata = Metadata()
    metadata.put(authorizationHeader, "${jsonWebToken.authScheme} ${jsonWebToken.token}")

    return MetadataUtils.newAttachHeadersInterceptor(metadata)
}
