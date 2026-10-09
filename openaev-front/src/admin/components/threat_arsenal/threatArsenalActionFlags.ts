import { type ThreatArsenalAction } from '../../../utils/api-types';

const computeActionFlags = (action: ThreatArsenalAction) => {
  // Dummy injectors were removed from the platform (#6779): actions whose
  // injector is not registered yet simply carry no injector type.
  const isUnregistered = action.action_injector_type == null;
  // `payload_collector_type` is optional in api-types, so a loose `!= null`
  // is required: a strict `!== null` would treat `undefined` (field absent
  // for manually created payloads) as "coming from a collector".
  const isFromCollector = action.action_payload?.payload_collector_type != null;
  return {
    // Update is disabled for actions coming from a collector or whose
    // injector is not registered yet.
    disableUpdate: isFromCollector || isUnregistered,
    disableDuplicate: action.action_payload == null || isUnregistered,
    disableJsonExport: action.action_payload == null || isUnregistered,
    // Orphaned actions (their injector was removed, hence the "question mark"
    // card) can always be purged. Otherwise delete is disabled for actions
    // without a payload and for collector actions that are not deprecated.
    disableDelete: isUnregistered
      ? false
      : (action.action_payload == null
        || (isFromCollector && action.action_payload.payload_status !== 'DEPRECATED')),
  };
};

export default computeActionFlags;
