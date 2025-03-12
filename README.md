# kuksa-java-sdk

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)
[![Gitter](https://img.shields.io/gitter/room/kuksa-val/community)](https://gitter.im/kuksa-val/community)

[![SDK:main <-> Databroker:main](https://github.com/eclipse-kuksa/kuksa-java-sdk/actions/workflows/daily_integration_main.yaml/badge.svg)](https://github.com/eclipse-kuksa/kuksa-java-sdk/actions/workflows/daily_integration_main.yaml?query=branch%3Amain)

This is a Java SDK for the [KUKSA Vehicle Abstraction Layer](https://github.com/eclipse-kuksa/kuksa-databroker).

## Overview

The KUKSA Java SDK allows you to interact with [VSS data](https://covesa.github.io/vehicle_signal_specification/)
from the [KUKSA Databroker](https://github.com/eclipse-kuksa/kuksa-databroker/tree/main/)
within a Java Application. The main functionality consists of fetching, updating and subscribing to VSS data.

Currently the following protocols are supported:
- kuksa.val.v1
- kuksa.val.v2

Unsupported protocols:
- sdv.databroker.v1

## kuksa.val.v1

You can interact with the Databroker using the kuksa.val.v2 interface. The interface is exposed by the org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection#kuksaValV2 property.

After successfully connecting the following methods are supported by org.eclipse.kuksa.connectivity.databroker.v1.DataBrokerConnection:
- fetch(FetchRequest): GetResponse
- fetch<T : VssNode>(VssNodeFetchRequest<T>)
- subscribe(SubscribeRequest, VssPathListener)
- subscribe(SubscribeRequest): Flow<SubscribeResponse>
- subscribe<T : VssNode>(VssNodeSubscribeRequest, VssNodeListener<T>)
- update(UpdateRequest): SetResponse
- update<T : VssNode>(request: VssNodeUpdateRequest<T>): VssNodeUpdateResponse
- streamedUpdate(StreamObserver<...Response>): StreamObserver<...Request>

## kuksa.val.v2

You can interact with the Databroker using the kuksa.val.v2 interface. The interface is exposed by the org.eclipse.kuksa.connectivity.databroker.DataBrokerConnection#kuksaValV1 property.

- fetchValue(FetchValueRequestV2): GetValueResponse
- fetchValues(FetchValuesRequestV2): GetValuesResponse
- subscribeById(SubscribeByIdRequestV2): Flow<SubscribeByIdResponse>
- subscribe(SubscribeRequestV2): Flow<SubscribeResponse> {
- actuate(ActuateRequestV2): ActuateResponse
- batchActuate(BatchActuateRequestV2): BatchActuateResponse
- listMetadata(ListMetadataRequestV2): ListMetadataResponse
- publishValue(PublishValueRequestV2): PublishValueResponse
- openProviderStream(StreamObserver<...Response>): StreamObserver<...Request>
- fetchServerInfo(): GetServerInfoResponse

## Integration

*app/build.gradle.kts*
```
implementation("org.eclipse.kuksa:kuksa-java-sdk:<VERSION>")

// uncomment for android
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<x.y.z>")

// uncomment for java
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:<x.y.z>")
```

The latest release version of the kuksa-java-sdk can be seen [here](https://github.com/eclipse-kuksa/kuksa-java-sdk/releases).


See the [quickstart guide](https://github.com/eclipse-kuksa/kuksa-java-sdk/tree/main/docs/QUICKSTART.md) for additional integration options.

### Maven Central

The KUKSA Java SDK is currently uploaded to [Maven Central](https://central.sonatype.com/search?q=org.eclipse.kuksa).
Snapshot builds are also available (but of course less stable): https://oss.sonatype.org/content/repositories/snapshots/

## Usage

> [!NOTE]
> The following snippet expects an **unsecure** setup of the Databroker. See the [quickstart guide](https://github.com/eclipse-kuksa/kuksa-java-sdk/blob/main/docs/QUICKSTART.md)
> for instructions on how to establish a **secure** connection to the Databroker.

```kotlin
private var dataBrokerConnection: DataBrokerConnection? = null

fun connectInsecure(host: String, port: Int) {
    lifecycleScope.launch {
        val managedChannel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()

        val connector = DataBrokerConnector(managedChannel)
        dataBrokerConnection = connector.connect()
        // Connection to the Databroker successfully established
    } catch (e: DataBrokerException) {
        // Connection to the Databroker failed
    }
}
```

Sample Code: 
```kotlin
fun fetch() {
    lifecycleScope.launch {
        val request = FetchRequest("Vehicle.Speed", listOf(Field.FIELD_VALUE))
        val response = dataBrokerConnection?.kuksaValV1.fetch(request) ?: return@launch
        val entry = entriesList.first() // Don't forget to handle empty responses
        val value = entry.value
        val speed = value.float
    }
}
```

More samples can be found here [here](https://github.com/eclipse-kuksa/kuksa-java-sdk/blob/main/samples/src/main/java/com/example/samples/Main.kt)
Refer to the [quickstart guide](https://github.com/eclipse-kuksa/kuksa-java-sdk/tree/main/docs/QUICKSTART.md) or
[class diagrams](https://github.com/eclipse-kuksa/kuksa-java-sdk/blob/main/docs/kuksa-sdk_class-diagram.puml) for
further insight into the KUKSA SDK API. 

## Requirements

- A working setup requires at least a running [KUKSA Databroker](https://github.com/eclipse-kuksa/kuksa-databroker/tree/main)
- Optional: The [KUKSA Databroker CLI](https://github.com/eclipse-kuksa/kuksa-databroker/tree/main/databroker-cli) can be used to manually feed data and test your app.
  See [this chapter](https://github.com/eclipse-kuksa/kuksa-databroker/tree/main?tab=readme-ov-file#reading-and-writing-vss-data-using-the-cli) on how to read and write data via the CLI.
- Optional: The [KUKSA Mock Provider](https://github.com/eclipse-kuksa/kuksa-mock-provider?tab=readme-ov-file#kuksa-mock-provider) can be used to simulate a "real" environment.

## Contribution

Please feel free to create [GitHub issues](https://github.com/eclipse-kuksa/kuksa-java-sdk/issues) and [contribute](https://github.com/eclipse-kuksa/kuksa-java-sdk/blob/main/docs/CONTRIBUTING.md).

## License

The KUKSA Java SDK is provided under the terms of the [Apache Software License 2.0](https://github.com/eclipse-kuksa/kuksa-java-sdk/blob/main/LICENSE).
