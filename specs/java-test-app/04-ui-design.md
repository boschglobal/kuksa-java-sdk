# UI Design: Java Test App

## 1. UI Hierarchy & Tab Layout
- **MainApp / Stage:**
  - TabPane containing 5 tabs:
    1. **Connection Tab:** Host, Port, JWT, TLS, V1/V2 Protocol toggle, Connect / Disconnect button, connection status label, error message banner.
    2. **Signals Tab:** Toolbar mode toggle (VSS Model / Raw Path), Type filters (Sensor, Actuator, Attribute, Branch). Left pane: Filterable `ListView<SignalItem>` (VSS Model) or Path `TextField` + History `ComboBox` + DataType selector (Raw Path). Right pane: Detail metadata grid, Value input, Field selector, Action buttons (Fetch, Fetch Node, Update, Update Node, Subscribe callbacks/flow/node), Mini-log area.
    3. **Actuators Tab:** Actuator selection list / Raw Path entry, Target value input, Actuate & Batch Actuate buttons, Streamed update / Provider stream toggle.
    4. **Subscriptions Tab:** Active subscription list, "+ by Path" and "+ by ID" dialogs, unsubscribe buttons, live event stream log.
    5. **Log Tab:** Filterable audit log, Clear Log, Server Info dialog button, Metadata viewer.

## 2. State Mapping & Accessibility
- All operational tabs (Signals, Actuators, Subscriptions, Log) are disabled (`isDisable = true`) when `ConnectionState != CONNECTED`.
- JavaFX controls receive state updates via `kotlinx-coroutines-javafx` observing `DataBrokerViewModel` `StateFlow` streams on `Dispatchers.Main`.
