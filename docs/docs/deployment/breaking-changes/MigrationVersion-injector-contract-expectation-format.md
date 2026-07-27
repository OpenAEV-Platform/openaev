# Injector contract expectation field format change

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

Starting with **OpenAEV [MigrationVersion]**, the expectation field structure inside `injector_contract_content` has changed.

### Removed: `predefinedExpectations` list

The separate `predefinedExpectations` list on the expectation contract field has been **removed**. All expectations — predefined or not — are now unified under a single `availableExpectations` list.

Each expectation item now carries an `expectation_is_predefined` flag indicating whether it was previously in the `predefinedExpectations` list.

### Before

```json
{
  "key": "expectations",
  "predefinedExpectations": [
    { "expectation_type": "DETECTION", "expectation_name": "Detection", "..." : "..." },
    { "expectation_type": "PREVENTION", "expectation_name": "Prevention", "..." : "..." }
  ],
  "availableExpectations": [
    { "expectation_type": "MANUAL", "expectation_name": "Manual", "..." : "..." }
  ]
}
```

### After

```json
{
  "key": "expectations",
  "availableExpectations": [
    { "expectation_type": "DETECTION", "expectation_name": "Detection", "expectation_is_predefined": true, "..." : "..." },
    { "expectation_type": "PREVENTION", "expectation_name": "Prevention", "expectation_is_predefined": true, "..." : "..." },
    { "expectation_type": "MANUAL", "expectation_name": "Manual", "expectation_is_predefined": false, "..." : "..." }
  ]
}
```

## Impact

- **Import/export**: JSON exports from older versions containing `predefinedExpectations` are handled transparently by a backward-compatible import (sanity check in `ThreatArsenalImportService`).
- **Custom integrations**: Any external tool or script that reads or writes `predefinedExpectations` in `injector_contract_content` must migrate to `availableExpectations` with the `expectation_is_predefined` flag.
- **Database**: A Flyway migration automatically merges existing `predefinedExpectations` into `availableExpectations` — no manual action required.

## Migration guide

1. Upgrade OpenAEV to the target version. The Flyway migration will:
    - Merge items from `predefinedExpectations` into `availableExpectations` with `expectation_is_predefined: true`.
    - Remove the `predefinedExpectations` field from all stored contracts.
2. If you have external scripts or integrations that read or write `predefinedExpectations`, update them to use `availableExpectations` and check the `expectation_is_predefined` flag.

!!! warning
    The `predefinedExpectations` field is no longer written. Re-importing old exports works (backward compatible), but re-exporting will use the new format only.

## Validation checklist after upgrade

1. Verify existing injector contracts display their expectations correctly in the UI.
2. Create a new Threat Arsenal action and confirm the stored `injector_contract_content` has no `predefinedExpectations` field and all expectations are under `availableExpectations`.
3. Import an old Threat Arsenal export and verify expectations are correctly migrated (formerly predefined expectations appear with `expectation_is_predefined: true`).

