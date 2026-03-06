# Spec: Typed Field Mapping between Actions (Input/Output Binding)

## Problem Statement

Today, the chaining graph can show which **output types** an action produces (via `ContractOutputType`: port, credentials, share, etc.), and events can filter on those types. But there is no way to express that an action **requires** specific fields as input.

### Current state

| Layer | Model | Type system | Example |
|---|---|---|---|
| Action outputs | `OutputParser` -> `ContractOutputElement` | `ContractOutputType` enum (22 types) | `credentials` (sub-fields: username, password), `portscan` (sub-fields: host, port, service) |
| Contract input fields | `injector_contract_content.fields[]` | `ContractFieldType` enum (16 types) | `text`, `asset`, `team`, `payload` |
| Payload arguments | `PayloadArgument` | `String type` (free text) | `"text"`, `"file"`, `"number"` |

**Key gap:** Neither contract fields nor payload arguments use the same type system as outputs. There is no semantic link between "this action needs a username from a credentials output" and "that action produces credentials with a username sub-field".

### Critical insight: output types are structured objects

Output types are NOT scalar values. Each `ContractOutputType` has **sub-fields** defined by its `OutputProcessor`:

| Output Type | Sub-fields (ContractOutputField) |
|---|---|
| `credentials` | `username` (Text, required), `password` (Text, required) |
| `portscan` | `host` (Text, required), `port` (Number, required), `service` (Text, required) |
| `username` | `username` (Text, required), `domain` (Text), `host` (Text) |
| `share` | `share_name` (Text, required), `permissions` (Text, required), `host` (Text) |
| `admin_username` | `username` (Text, required), `host` (Text) |
| `group` | `group_name` (Text, required), `member_count` (Text), `rid` (Text), `host` (Text) |
| `computer` | `computer_name` (Text, required), `host` (Text) |
| `delegation` | `account` (Text, required), `delegation_type` (Text), `rights_to` (Text), `host` (Text) |
| `sid` | `sid` (Text, required), `host` (Text) |
| `vulnerability` | `name` (Text, required), `status` (Text, required), `details` (Text), `host` (Text) |
| `cve` | `id` (Text, required), `host` (Text, required), `severity` (Text, required) |
| `kerberoastable_account` | `username` (Text, required), `hash` (Text), `host` (Text) |
| `asreproastable_account` | `username` (Text, required), `hash` (Text), `host` (Text) |
| `account_with_password_not_required` | `account` (Text, required), `status` (Text), `host` (Text) |
| `password_policy` | `key` (Text, required), `value` (Text, required), `host` (Text) |

**Therefore, an input argument binds to a specific sub-field of an output type, not to the type itself.**

For example, an argument `target_user` doesn't need "a credentials" -- it needs `credentials.username`.

### Target state

Introduce a **data source** annotation on contract fields and payload arguments that references a **(input_type, sub-field)** pair. This creates a precise, field-level link: Action A outputs `credentials` with sub-field `username` -> Action B's argument `target_user` is sourced from `credentials.username`.

---

## Design

### 1. New concept: `data_source` (input_type + field binding)

Add an optional `data_source` object to:
- **Contract content fields** (`injector_contract_content.fields[].data_source`)
- **Payload arguments** (`PayloadArgument.dataSource`)

Structure:
```json
{
  "input_type": "credentials",   // ContractOutputType value
  "input_field": "username"      // ContractOutputField key within that type
}
```

This means: "this input can be auto-populated from the `username` sub-field of an upstream `credentials` output."

When absent (null), the input is a plain user-provided value with no chaining semantics (backward compatible).

### 2. Contract content JSON change

**Before:**
```json
{
  "fields": [
    {
      "key": "target_host",
      "type": "text",
      "label": "Target Host",
      "mandatory": true,
      "cardinality": "1"
    },
    {
      "key": "username",
      "type": "text",
      "label": "Username",
      "mandatory": true,
      "cardinality": "1"
    }
  ]
}
```

**After:**
```json
{
  "fields": [
    {
      "key": "target_host",
      "type": "text",
      "label": "Target Host",
      "mandatory": true,
      "cardinality": "1",
      "data_source": {
        "input_type": "portscan",
        "input_field": "host"
      }
    },
    {
      "key": "username",
      "type": "text",
      "label": "Username",
      "mandatory": true,
      "cardinality": "1",
      "data_source": {
        "input_type": "credentials",
        "input_field": "username"
      }
    }
  ]
}
```

### 3. PayloadArgument model change

**Before:**
```java
@Data
public class PayloadArgument {
  private String type;        // "text", "file", "number"
  private String key;
  private String defaultValue;
  private String description;
  private String separator;
}
```

**After:**
```java
@Data
public class PayloadArgument {
  private String type;                        // UI type: "text", "file", "number"
  private String key;
  private String defaultValue;
  private String description;
  private String separator;

  @JsonProperty("data_source")
  @Schema(description = "Semantic binding to an upstream output field")
  private DataSource dataSource;              // nullable
}
```

New embedded model:
```java
@Data
public class DataSource {
  @JsonProperty("input_type")
  private String outputType;     // ContractOutputType value: "credentials", "portscan", etc.

  @JsonProperty("input_field")
  private String outputField;    // Sub-field key: "username", "host", "port", etc.
}
```

### 4. ContractElement base class change

In `ContractElement.java` (framework), add:

```java
@Getter
private final DataSource dataSource;  // nullable
```

This propagates through all subclasses (`ContractText`, `ContractSelect`, etc.).

---

## Chaining Graph Impact

### 5.1 Extracting input bindings from a step

New utility function in `logicUtils.ts`:

```typescript
interface DataSourceBinding {
  argumentKey: string;      // e.g., "target_host"
  outputType: string;       // e.g., "portscan"
  outputField: string;      // e.g., "host"
}

/**
 * Extract data source bindings from a step's contract/payload arguments.
 * Returns the list of arguments that have a data_source annotation.
 */
export const extractInputBindings = (step: WorkflowStep): DataSourceBinding[] => {
  // Parse inject_content or contract content to find fields with data_source
};
```

### 5.2 Visual: Input binding chips on action nodes

On `NodeAction`, show **input binding chips** with the source type and field:

```
+----------------------------------------------------+
| [NetExec SMB Auth]                  Action   T1110  |
|                                                     |
| Needs: [portscan.host] [credentials.username]       |  <- NEW: input bindings
| Produces: [share] [admin_username] [sid]            |  <- existing output types
+----------------------------------------------------+
```

Each input chip shows `outputType.outputField` to make it clear what sub-field is expected.

### 5.3 Edge visualization: field flow

When highlighting a node, show directed field-flow edges with the specific binding:

```
[Nmap Scan]                      [NetExec SMB]
 outputs: portscan                needs: portscan.host
          portscan.host ---------> target_host
          portscan.port ---------> target_port
                                  needs: credentials.username
                    [no provider!] ----x username
```

The graph shows:
- **Green dashed edges**: resolved bindings (upstream action produces the needed input_type with the right sub-field)
- **Red dashed edges**: unresolved bindings (no upstream action produces this input_type, or the sub-field doesn't exist)

### 5.4 Health check: unresolved input bindings

New health warning (extends existing system):

> Action "NetExec SMB Auth" argument `username` requires `credentials.username` but no upstream action in its chain produces output type `credentials`.

This is more precise than the current event-level field warnings because it validates at the sub-field level.

---

## Resolution Algorithm

Given a step S with a `data_source` binding `{input_type: "credentials", input_field: "username"}`:

1. Walk upstream through the chain (DEPEND_ON edges + field provisioning)
2. Find all upstream actions that produce output type `credentials` (via their OutputParser/ContractOutputElement)
3. For each producing action, verify that the `credentials` type has a sub-field `username` (via OutputProcessor.getFields())
4. If found: binding is **resolved** (green)
5. If not found: binding is **unresolved** (red warning)

For runtime execution (future): when the workflow engine reaches step S, it looks up the most recent finding of type `credentials` from an upstream action, extracts the `username` sub-field, and injects it into argument `username`.

---

## Implementation Plan

### Phase 1: Backend model

1. **Create `DataSource` embedded model** (`openaev-model`)
   - Simple POJO with `outputType` and `outputField`
   - JSON serialization: `{ "input_type": "...", "input_field": "..." }`

2. **Add `dataSource` to `PayloadArgument`** (`openaev-model`)
   - Optional field, nullable
   - No migration needed (PayloadArgument is embedded JSON in Payload)

3. **Add `dataSource` to `ContractElement`** (`openaev-framework`)
   - Optional field, nullable
   - Serialized in contract content JSON as `data_source`

4. **Update contract content parsing** (`InjectorContractContentUtils`)
   - Deserialize `data_source` from fields when parsing contract content

5. **New API endpoint: GET /api/input_types/fields**
   - Returns the full catalog: for each ContractOutputType, list its sub-fields
   - Uses `OutputProcessorFactory` to call `getFields()` on each processor
   - Frontend uses this to populate the data_source field picker

### Phase 2: Frontend - Payload & contract forms

6. **Payload argument form**: add `data_source` picker
   - Step 1: select input_type from dropdown (credentials, portscan, etc.)
   - Step 2: select input_field from dropdown (filtered by selected type)
   - File: `openaev-front/src/admin/components/payloads/form/`

7. **Contract field form**: same `data_source` picker
   - When manually defining contract fields

### Phase 3: Frontend - Chaining graph

8. **Extract input bindings** from step data in `logicUtils.ts`
   - New function `extractInputBindings(step)`
   - Reads contract content fields + payload arguments with `data_source`

9. **NodeAction: show input binding chips**
   - Display "Needs:" row with `outputType.outputField` chips
   - Green chip = resolved, Red chip = unresolved

10. **Extend graph edges**: binding flow
    - New edge type: upstream action output -> downstream action input (matching data_source)
    - Label edges with the field path (e.g., "credentials.username")

11. **Health warnings for unresolved bindings**
    - Extend `LogicHealthWarnings` to detect missing binding providers
    - "Show compatible actions" filters by input_type that has the needed sub-field

### Phase 4: Annotation of existing contracts/payloads

12. **Annotate NetExec payload arguments** with `data_source`
    - `target` -> `{ input_type: "portscan", input_field: "host" }`
    - `username` -> `{ input_type: "credentials", input_field: "username" }`
    - `password` -> `{ input_type: "credentials", input_field: "password" }`

13. **Annotate Nmap payload arguments**
    - `target` -> no data_source (root action, user provides the target)
    - Or: `target` -> `{ input_type: "ipv4", input_field: null }` for scalar types

---

## Handling Scalar Output Types

Some output types are scalar (not structured objects):
- `text` -> single Text value
- `number` -> single Number value
- `port` -> single Number value
- `ipv4` -> single Text value
- `ipv6` -> single Text value

For these, `input_field` is `null` because there are no sub-fields. The binding is simply:
```json
{
  "input_type": "ipv4",
  "input_field": null
}
```

This means: "take the raw value of the ipv4 output."

---

## Examples

### Example 1: Nmap -> Port Event -> NetExec SMB

```
Nmap SYN Scan
  outputs: [portscan] with fields {host, port, service}
  inputs: [] (root action, user provides target)
       |
       v  (DEPEND_ON edge)
Port Found (event)
  condition: portscan.port EQ 445
       |
       v  (DEPEND_ON edge)
NetExec SMB Enum
  outputs: [share, credentials, admin_username]
  inputs:
    - target: data_source = {portscan, host}     -> RESOLVED from Nmap
    - port:   data_source = {portscan, port}     -> RESOLVED from Nmap
```

### Example 2: Credential Chaining

```
NetExec SMB Auth (initial creds from user)
  outputs: [credentials] with fields {username, password}
           [admin_username] with fields {username, host}
  inputs:
    - target:   data_source = {portscan, host}    -> from Nmap upstream
    - username: data_source = null                 -> user provides
    - password: data_source = null                 -> user provides
       |
       v
Admin Found (event)
  condition: admin_username.username EXISTS
       |
       v
Lateral Move (PSExec)
  outputs: []
  inputs:
    - target:   data_source = {admin_username, host}      -> RESOLVED from NetExec
    - username: data_source = {admin_username, username}   -> RESOLVED from NetExec
    - password: data_source = {credentials, password}     -> RESOLVED from NetExec
```

### Example 3: Unresolved binding

```
Standalone NetExec (no upstream)
  inputs:
    - target:   data_source = {portscan, host}    -> UNRESOLVED (no upstream produces portscan)
    - username: data_source = {credentials, username} -> UNRESOLVED

  Health warning: "NetExec argument 'target' expects portscan.host but no upstream
                   action produces output type 'portscan'"
```

---

## Backward Compatibility

- `data_source` is **optional** (null by default)
- Existing contracts and payloads continue to work unchanged
- The chaining graph gracefully handles steps with no data_source bindings (shows only output chips)
- No database migration required (PayloadArgument is embedded JSON, contract content is JSON)

## Future Extensions

- **Auto-population at runtime**: workflow engine resolves data_source bindings from upstream findings
- **Type compatibility**: `admin_username.username` could satisfy a binding expecting `credentials.username` (both are "username" fields of Text type)
- **Cardinality validation**: if an input expects 1 value but upstream produces N findings, warn about fan-out
- **Binding override**: user can override an auto-resolved binding with a manual value
- **Visual binding editor**: drag from an output chip sub-field to an input chip to create/change bindings
