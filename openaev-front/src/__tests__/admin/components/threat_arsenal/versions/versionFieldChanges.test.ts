import { describe, expect, it } from 'vitest';

import versionFieldChanges from '../../../../../admin/components/threat_arsenal/versions/versionFieldChanges';
import { type ThreatArsenalActionFullOutput } from '../../../../../utils/api-types';

const aVersion = (overrides: Partial<ThreatArsenalActionFullOutput> = {}) => ({
  action_id: 'action',
  action_labels: { en: 'Discover network shares' },
  action_description: 'Lists the shares',
  action_platforms: ['Windows'],
  command_executor: 'cmd',
  command_content: 'net share',
  action_arguments: [],
  ...overrides,
}) as ThreatArsenalActionFullOutput;

const name = (details: ThreatArsenalActionFullOutput) => details.action_labels.en;

describe('versionFieldChanges', () => {
  it('given_aChangedCommand_should_returnOnlyThatField', () => {
    // Act
    const rows = versionFieldChanges(aVersion(), aVersion({ command_content: 'net view' }), name);

    // Assert
    expect(rows).toEqual([{
      id: 'COMMAND_CONTENT',
      field: 'Command',
      before: 'net share',
      after: 'net view',
    }]);
  });

  it('given_aNewNameAndPlatform_should_listThemInFormOrder', () => {
    // Act
    const rows = versionFieldChanges(aVersion(), aVersion({
      action_labels: { en: 'Discover shares' },
      action_platforms: ['Windows', 'Linux'],
    }), name);

    // Assert
    expect(rows.map(row => row.id)).toEqual(['NAME', 'PLATFORMS']);
    expect(rows[1].after).toBe('Windows\nLinux');
  });

  it('given_emptyValuesOnly_should_returnNoChange', () => {
    // Act
    const rows = versionFieldChanges(aVersion({ action_cleanup_command: undefined }), aVersion({ action_cleanup_command: '' }), name);

    // Assert
    expect(rows).toEqual([]);
  });
});
