import { Chip, Paper, Text } from '@filigran/design-system';

import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import { mitigationsOf } from './mitreMitigations';

interface Props {
  // ATT&CK technique ids of the action, e.g. T1003.001.
  techniques: string[];
}

// PROTOTYPE: the "Generic" mitigations of an action are the ones MITRE ATT&CK publishes for its
// techniques. Nothing is generated: the list only depends on the techniques.
const MitreMitigationList = ({ techniques }: Props) => {
  const { t } = useFormatter();
  const groups = mitigationsOf(techniques);

  if (groups.length === 0) {
    return <Empty message={t('This action has no ATT&CK technique, so there is no generic mitigation to show.')} />;
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <Text variant="content-caption" className="text-default-secondary">
        {t('Generic mitigations published by MITRE ATT&CK for the techniques of this action. They do not depend on a security product.')}
      </Text>
      {groups.map(group => (
        <section
          key={group.technique}
          aria-label={group.technique}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
          >
            <Chip label={group.technique} severity="neutral" />
            <Text variant="content-base-bold">{group.name ?? t('Unknown technique')}</Text>
          </div>
          {group.mitigations.length === 0
            ? (
                <Text variant="content-compact" className="text-default-secondary">
                  {t('MITRE lists no mitigation for this technique: it abuses a normal feature of the system and cannot be easily prevented. Rely on detection (Vendor-specific tab).')}
                </Text>
              )
            : group.mitigations.map(mitigation => (
                <Paper
                  key={mitigation.id}
                  padding={16}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                  }}
                >
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    flexWrap: 'wrap',
                  }}
                  >
                    <a href={mitigation.url} target="_blank" rel="noopener noreferrer" aria-label={`${mitigation.id} ${mitigation.name}`}>
                      <Chip label={mitigation.id} severity="info" />
                    </a>
                    <Text variant="content-base-bold">{mitigation.name}</Text>
                  </div>
                  <Text variant="content-compact">{mitigation.text}</Text>
                </Paper>
              ))}
        </section>
      ))}
    </div>
  );
};

export default MitreMitigationList;
