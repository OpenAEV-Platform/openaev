import { type ThreatArsenalAction, type ThreatArsenalActionFullOutput } from '../../../utils/api-types';

/** Query parameter opening an action's drawer in the Threat Arsenal (`?action=<id>`), e.g. from a notification. */
export const ACTION_PARAM = 'action';

// The approver's review queue and the author's own submissions (tabs of the Threat Arsenal).
export const THREAT_ARSENAL_TO_REVIEW_PATH = '/admin/threat-arsenal/to-review';
export const THREAT_ARSENAL_MY_CHANGES_PATH = '/admin/threat-arsenal/my-changes';

/**
 * The list row of an action built from its detail, so a deep link can open the drawer of an action
 * that is not on the current page of the list.
 */
export const toThreatArsenalAction = (full: ThreatArsenalActionFullOutput): ThreatArsenalAction => ({
  injector_contract_id: full.action_id,
  injector_contract_external_id: full.action_external_id,
  injector_contract_updated_at: full.action_updated_at,
  action_labels: full.action_labels,
  action_platforms: full.action_platforms,
  action_domains_ids: full.action_domains ?? [],
  action_attack_patterns_ids: full.action_attack_patterns ?? [],
  action_tags_ids: full.action_tags,
  action_injector_type: full.action_type,
  // Only payload-based actions have an approval status.
  action_payload: full.action_approval_status
    ? {
        payload_id: full.action_payload_id,
        payload_version: full.action_payload_version,
        payload_type: full.action_type,
        payload_collector_type: full.action_collector_type,
        payload_status: full.action_status,
        payload_approval_status: full.action_approval_status,
      }
    : undefined,
});
