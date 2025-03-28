## Introduction

Get instantly bootstrapped into the world of the KUKSA Java SDK with the following code snippets!

## Integration

*app/build.gradle.kts*

```
implementation("org.eclipse.kuksa:kuksa-java-sdk:<VERSION>")
```

## Connecting to the Databroker

You can use the following snippet for a simple (unsecure) connection to the Databroker.

*Kotlin*

```kotlin
private var dataBrokerConnection: DataBrokerConnection? = null

fun connectInsecure(host: String, port: Int) {
    lifecycleScope.launch {
        val managedChannel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()

        // or jsonWebToken = null when authentication is disabled
        val jsonWebToken = JsonWebToken("someValidJwt") 
        val connector = DataBrokerConnector(managedChannel, jsonWebToken)
        try {
            dataBrokerConnection = connector.connect()
            // Connection to the Databroker successfully established
        } catch (e: DataBrokerException) {
            // Connection to the Databroker failed
        }
    }
}
```

*Java*

```java
void connectInsecure(String host, int port) {
    ManagedChannel managedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();

    // or jsonWebToken = null when authentication is disabled
    JsonWebToken jsonWebToken = new JsonWebToken("someValidJwt");

    DataBrokerConnector connector = new DataBrokerConnector(managedChannel, jsonWebToken);
    connector.connect(new CoroutineCallback<DataBrokerConnection>() {
        @Override
        public void onSuccess(DataBrokerConnection result) {
            dataBrokerConnection = result;
        }
        
        @Override
        public void onError(@NonNull Throwable error) {
            // Connection to the Databroker failed
        }
    });
}
```

## Interacting with the Databroker

*Kotlin*

```kotlin
fun fetch() {
    lifecycleScope.launch {
        val request = FetchRequest("Vehicle.Speed", Field.FIELD_VALUE)
        val response = dataBrokerConnection?.kuksaValV1.fetch(request) ?: return@launch
        val entry = response.entriesList.first() // Don't forget to handle empty responses
        val value = entry.value
        val speed = value.float
    }
}

fun update() {
    lifecycleScope.launch {
        val request = UpdateRequest("Vehicle.Speed", Field.FIELD_VALUE)
        val datapoint = Datapoint.newBuilder().setFloat(100f).build()
        dataBrokerConnection?.kuksaValV1.update(request, datapoint)
    }
}

fun subscribe() {
    val request = SubscribeRequest("Vehicle.Speed", Field.FIELD_VALUE)
    val listener = object : VssPathListener {
        override fun onEntryChanged(entryUpdates: List<KuksaValV1.EntryUpdate>) {
            entryUpdates.forEach { entryUpdate ->
                val updatedValue = entryUpdate.entry

                // handle entry change
                when (val vssPath = updatedValue.path) {
                    "Vehicle.Speed" -> {
                        val speed = updatedValue.value.float
                    }
                    else -> throw IllegalArgumentException("Unhandled vssPath: $vssPath")
            }
        }
    }

    dataBrokerConnection?.kuksaValV1.subscribe(request, listener)
}
```

*Java*

```java
void fetch() {
    FetchRequest request = new FetchRequest("Vehicle.Speed", Types.Field.FIELD_VALUE);
    dataBrokerConnection.kuksaValV1.fetch(request, new CoroutineCallback<GetResponse>() {
        @Override
        public void onSuccess(GetResponse result) {
            result.entriesList.first(); // Don't forget to handle empty responses
            Types.DataEntry dataEntry = result.getEntriesList().get(0);
            Datapoint datapoint = dataEntry.getValue();
            float speed = datapoint.getFloat();
        }
    });
}

void update() {
    Datapoint datapoint = Datapoint.newBuilder().setFloat(100f).build();
    UpdateRequest request = new UpdateRequest("Vehicle.Speed", datapoint, Types.Field.FIELD_VALUE);
    dataBrokerConnection.kuksaValV1.update(request, new CoroutineCallback<KuksaValV1.SetResponse>() {
        @Override
        public void onSuccess(KuksaValV1.SetResponse result) {
        // handle result
        }
    });
}

void subscribe() {
    SubscribeRequest request = new SubscribeRequest("Vehicle.Speed", Types.Field.FIELD_VALUE);
    dataBrokerConnection.kuksaValV1.subscribe(request, new VssPathListener() {
        @Override
        public void onEntryChanged(@NonNull List<EntryUpdate> entryUpdates) {
            for (KuksaValV1.EntryUpdate entryUpdate : entryUpdates) {
                Types.DataEntry updatedValue = entryUpdate.getEntry();
                
                // handle entry change
                String vssPath = updatedValue.getPath();
                switch (vssPath) {
                    case "Vehicle.Speed" -> {
                        float speed = updatedValue.getValue().getFloat();
                    }
                    default -> throw new IllegalArgumentException("Unhandled vssPath: " + vssPath);
                }
            }
        }
        
        @Override
        public void onError(@NonNull Throwable throwable) {
            // handle error
        }
    });
}
```
