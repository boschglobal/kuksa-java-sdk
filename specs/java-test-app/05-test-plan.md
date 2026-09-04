# Test Plan: Java Test App & Mock Provider

## 1. Test Pyramid & Scope
- **Unit Tests:**
  - `ConfigValidatorTest`: Valid and invalid connection configs (UT-1, UT-2).
  - `LogEntryTest`: Formatting and serialization of log entries (UT-3).
  - `SignalItemTest`: Extraction of signal properties from VSS nodes (UT-4).
  - `BehaviorRuleEngineTest`: Direct, ramp, and toggle transform computations (UT-5, UT-6, UT-7).
  - `DataTypeSerializerTest`: String to Protobuf v1 Datapoint and v2 Value conversions for all 8 data types (UT-8, UT-9, UT-10).
  - `PathHistoryTest`: Bounded ring buffer eviction and order (UT-11).
  - `DataBrokerViewModelTest`: Connection lifecycle, state transitions, and disconnect handling.
  - `V1ApiViewModelTest`: All 8 `KuksaValV1Protocol` method delegations and logging.
  - `V2ApiViewModelTest`: All 10 `KuksaValV2Protocol` method delegations and logging.
  - `VssSignalRepositoryTest`: Traversal of generated VSS model tree.
- **Integration Tests:**
  - `MockProviderIntegrationTest` / Databroker integration with `InsecureDataBrokerDockerContainer` (or in-process tests).
- **Target Coverage:**
  - Minimum 90% Line Coverage / 85% Branch Coverage on new domain, model, and viewmodel packages.
