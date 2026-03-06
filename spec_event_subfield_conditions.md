# Spec: Sub-field level event conditions

## Problem

Event conditions currently filter on **output types only** (e.g. `credentials`, `portscan`). The `condition_key` is a bare type name, and the `condition_value` is compared against... nothing specific, because the filter evaluation is not yet implemented (`isFilterConditionValid` is a TODO).

This is inconsistent with the input binding system where arguments use two separate fields (`input_type` + `input_field`). Events should use the same pattern.

### Current state

```
Event: "Admin Found"
  condition_key: "admin_username"     <- output type only, no sub-field
  condition_type: IS_NOT_NULL
  condition_value: ""
```

This says "trigger when an `admin_username` finding exists" but you can't say "trigger when the `username` sub-field of an `admin_username` finding equals `Administrator`".

### Target state

```
Event: "Admin Found"
  condition_key:   "admin_username"   <- finding type
  condition_field: "username"         <- sub-field (new column, nullable)
  condition_type:  EQ
  condition_value: "Administrator"
```

Two separate fields, same pattern as `data_source.input_type` / `data_source.input_field` on arguments.

---

## Design

### 1. New column: `condition_field`

Add an optional `condition_field` column to the `conditions` table:

| Column | Type | Nullable | Description |
|---|---|---|---|
| `condition_key` | `VARCHAR(255)` | yes | Finding type (e.g. `credentials`, `portscan`) — **unchanged** |
| `condition_field` | `VARCHAR(255)` | yes | Sub-field within the finding type (e.g. `username`, `port`) — **new** |
| `condition_value` | `VARCHAR(255)` | yes | Value to compare against — **unchanged** |
| `condition_type` | `enum` | no | Operator (EQ, GT, IS_NOT_NULL...) — **unchanged** |

When `condition_field` is `null`: the condition applies to the finding type as a whole (existence check).
When `condition_field` is set: the condition applies to that specific sub-field value.

### 2. Consistency with input bindings

| Concept | Arguments (data_source) | Event conditions |
|---|---|---|
| Type | `input_type: "credentials"` | `condition_key: "credentials"` |
| Sub-field | `input_field: "username"` | `condition_field: "username"` |
| Value | — | `condition_value: "admin"` |

Same UX everywhere: first dropdown = type, second dropdown = sub-field.

---

## Model changes

### 3. Backend: Condition entity

```java
// Condition.java — add new column
@Column(name = "condition_field")
@JsonProperty("condition_field")
@Schema(description = "Sub-field within the finding type to filter on")
private String field;
```

### 4. Backend: ConditionInput

```java
// ConditionInput.java — add new field
@JsonProperty("condition_field")
@Nullable
private String field;
```

### 5. Backend: WorkflowOutputDto.ConditionOutputDto

Add `condition_field` to the DTO so the frontend receives it.

### 6. Database migration

```sql
ALTER TABLE conditions ADD COLUMN condition_field VARCHAR(255);
```

Simple nullable column, no data migration needed. Existing conditions have `condition_field = NULL` which means "match on type only" (backward compatible).

### 7. Frontend: WorkflowCondition type

```typescript
// api-types-custom.d.ts
export interface WorkflowCondition {
  condition_id: string;
  condition_key?: string;       // finding type: "credentials"
  condition_field?: string;     // sub-field: "username" (new)
  condition_value?: string;
  condition_type: ConditionType;
  step_from_id?: string;
  condition_parent_id?: string;
  condition_created_at?: string;
  condition_updated_at?: string;
}
```

---

## Frontend changes

### 8. LogicConditionRuleRow: two-step picker

Replace the single `FINDING_TYPES` dropdown with a two-step picker, reusing the `/api/output_types` catalog (same singleton cache as PayloadArgumentsField):

**Step 1: Finding type dropdown** (same list as today, but from catalog)
```
[ credentials  v ]
```

**Step 2: Sub-field dropdown** (new, optional)
- Populated from the catalog's `fields[]` for the selected type
- If left empty: condition applies to the type as a whole
- If selected: condition applies to that specific sub-field value

```
[ credentials  v ]  [ username  v ]  [ EQ  v ]  [ "admin"       ]
```

Sent to backend as two separate fields:
- `condition_key: "credentials"`
- `condition_field: "username"`

#### Operator filtering

| Sub-field selected? | Available operators |
|---|---|
| No (bare type) | `IS_NOT_NULL`, `IS_NULL` only |
| Yes (sub-field) | All: `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NIN`, `IS_NULL`, `IS_NOT_NULL` |

Without a sub-field you can't compare a structured object to a scalar, so only existence checks make sense.

### 9. LogicConditionRuleRow: ConditionRule type update

```typescript
interface ConditionRule {
  key: string;            // finding type
  field: string;          // sub-field (new, empty string = no sub-field)
  operator: ConditionType;
  value: string;
  caseSensitive: boolean;
}
```

### 10. NodeEvent: display sub-field in condition chips

```
+-------------------------------------------+
| [Port 445 Found]                 Event    |
|                                           |
| portscan.port EQ 445                      |
| portscan.service EQ "microsoft-ds"        |
+-------------------------------------------+
```

Display format: `condition_field` is set -> `key.field OP value`, else `key OP value`.

### 11. Health warnings & graph: no change needed

`getActionsProvisioningField` already matches on `condition_key` which remains the bare finding type. No parsing or splitting needed — `condition_key` stays clean.

Same for `getDownstreamStepIds` / `getUpstreamStepIds`: they match `c.condition_key` against output types, and `condition_key` is still just `"portscan"` or `"credentials"`. No change.

---

## Backend changes

### 12. Filter condition evaluation (ConditionService)

Implement `isFilterConditionValid`:

```java
public Condition isFilterConditionValid(Condition condition, String input, String data) {
  String findingType = condition.getKey();     // "credentials"
  String subField = condition.getField();      // "username" or null
  String expectedValue = condition.getValue(); // "admin"
  ConditionType operator = condition.getType();

  // 1. Parse input (JSON findings from upstream step output)
  // 2. Find findings matching findingType
  // 3. If subField is null:
  //      -> IS_NOT_NULL: return match if any finding of this type exists
  //      -> IS_NULL: return match if no finding of this type exists
  // 4. If subField is set:
  //      -> Extract subField value from finding_value JSON
  //      -> Apply operator against expectedValue
  // 5. Return Condition execution result if matched, null if not
}
```

### 13. Finding value extraction

When evaluating `condition_key="credentials"`, `condition_field="username"`, `EQ`, `"admin"`:

1. Get findings from upstream step output where `finding_type = "credentials"`
2. Parse the finding's `finding_value` (JSON object for structured types)
3. Extract `finding_value["username"]`
4. Compare with `"admin"` using the EQ operator

For existence checks (`condition_field=null`, `condition_key="portscan"`, `IS_NOT_NULL`):
1. Check if any finding with `finding_type = "portscan"` exists
2. If yes: condition satisfied

---

## Backward compatibility

- Existing conditions have `condition_field = NULL` -> type-level existence check (unchanged behavior)
- `condition_key` stays as the bare finding type -> no impact on graph logic, health warnings, or provisioning checks
- The new column is nullable with no default -> no data migration needed
- Old events continue to work as-is

---

## Implementation plan

### Phase A: Model (backend + frontend types)

1. **Database migration**: add `condition_field` column
2. **Condition entity**: add `field` property
3. **ConditionInput / WorkflowOutputDto**: add `condition_field`
4. **WorkflowCondition TypeScript type**: add `condition_field`
5. **ConditionRule**: add `field` property

### Phase B: Frontend (condition form)

6. **Fetch output types catalog** in LogicConditionRuleRow (reuse `/api/output_types` singleton cache from PayloadArgumentsField)
7. **Two-step picker**: type dropdown + sub-field dropdown
8. **Filter operators** based on whether a sub-field is selected
9. **Send `condition_field`** in ConditionCreateInput

### Phase C: Frontend (display)

10. **NodeEvent**: show `key.field OP value` in condition chips
11. **LogicEventForm**: load/save the `field` when editing existing events

### Phase D: Backend (runtime evaluation)

12. **Implement `isFilterConditionValid`** with sub-field extraction
13. **Finding value extraction** from JSON finding_value

---

## Examples

### Example 1: Port-specific event

```
Nmap SYN Scan
  outputs: [portscan] -> {host, port, service}
       |
       v
Port 445 Found (event)
  condition_key: "portscan", condition_field: "port", EQ "445"
  condition_key: "portscan", condition_field: "service", EQ "microsoft-ds"
  logic: AND
       |
       v
NetExec SMB Enum
  inputs: target <- portscan.host
```

### Example 2: Credential-based event

```
NetExec SMB Auth
  outputs: [credentials] -> {username, password}
  outputs: [admin_username] -> {username, host}
       |
       v
Admin Account Found (event)
  condition_key: "admin_username", condition_field: "username", EQ "Administrator"
       |
       v
Lateral Move (PSExec)
  inputs: target <- admin_username.host
  inputs: username <- admin_username.username
```

### Example 3: Existence check (backward compatible)

```
NetExec SMB Enum
  outputs: [share, credentials]
       |
       v
Credentials Discovered (event)
  condition_key: "credentials", condition_field: null, IS_NOT_NULL
       |
       v
...next action...
```
