import { searchAttackPatternsByIdAsOption } from '../../../../../actions/AttackPattern';

/**
 * Resolves MITRE ATT&CK pattern ids (as carried on `inject_attack_pattern`,
 * `pattern.inject_attack_pattern`, etc.) to their human-readable technique
 * name/external id, so Executive reports can show a business-friendly
 * finding name instead of a raw internal id, while Technical reports can
 * still show the exact MITRE mapping (external id + name).
 */
export interface AttackPatternNameInfo {
  name: string;
  externalId?: string;
}

export const fetchAttackPatternNames = async (
  ids: string[],
): Promise<Record<string, AttackPatternNameInfo>> => {
  const uniqueIds = [...new Set(ids.filter(Boolean))];
  if (uniqueIds.length === 0) return {};
  try {
    const response = await searchAttackPatternsByIdAsOption(uniqueIds);
    const options: {
      id: string;
      label: string;
    }[] = response?.data ?? [];
    return Object.fromEntries(options.map(o => [o.id, { name: o.label }]));
  } catch {
    return {};
  }
};
