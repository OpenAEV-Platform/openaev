import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import RemediationMitigationPanel, { type PanelContext } from './RemediationMitigationPanel';

type Section = 'remediation' | 'vendor' | 'generic';

interface Props {
  // Name of the payload (or inject) the tab is for. Resolved to a sample payload.
  payloadName?: string | null;
  // Review / edit / delete (Threat Arsenal and attack path). Atomic testing is read-mostly.
  editable?: boolean;
  context?: PanelContext;
  // Types of the findings the action produced, shown on the cards.
  findingTypes?: string[];
  // Hosts where the remediation must be applied.
  affectedHosts?: string[];
  // ATT&CK technique ids of the action: the generic mitigations are the ones MITRE publishes for them.
  techniques?: string[];
}

// PROTOTYPE: the single "Remediation" tab of an action. Remediation removes the weakness; the mitigations stop or
// detect the attack, either with a security product (vendor-specific) or with what MITRE ATT&CK publishes for
// its techniques (generic). They are three views of the same tab.
const RemediationMitigationTabs = ({ payloadName, editable, context, findingTypes, affectedHosts, techniques }: Props) => {
  const { t } = useFormatter();
  const [section, setSection] = useState<Section>('remediation');
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      <Tabs value={section} onValueChange={value => setSection(value as Section)} panels="external">
        <TabsList aria-label={t('Remediation')}>
          <TabsTrigger value="remediation">{t('Remediation')}</TabsTrigger>
          <TabsTrigger value="vendor">{t('Vendor-specific mitigation')}</TabsTrigger>
          <TabsTrigger value="generic">{t('Generic mitigation')}</TabsTrigger>
        </TabsList>
      </Tabs>
      {section === 'remediation'
        ? (
            <RemediationMitigationPanel
              category="remediation"
              payloadName={payloadName}
              editable={editable}
              context={context}
              findingTypes={findingTypes}
              affectedHosts={affectedHosts}
            />
          )
        : (
            <RemediationMitigationPanel
              category="mitigation"
              mitigationMode={section}
              payloadName={payloadName}
              editable={editable}
              context={context}
              findingTypes={findingTypes}
              techniques={techniques}
              onSwitchToRemediation={() => setSection('remediation')}
            />
          )}
    </div>
  );
};

export default RemediationMitigationTabs;
