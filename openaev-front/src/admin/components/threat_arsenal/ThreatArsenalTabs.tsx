import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../components/i18n';

export type ThreatArsenalTab = 'installed' | 'available';

interface Props { active: ThreatArsenalTab }

/** The routed tabs of the Threat Arsenal: Active and Available. */
const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();

  const tab = (tabValue: ThreatArsenalTab, label: string, to: string) => (
    <TabsTrigger key={tabValue} value={tabValue} asChild>
      <Link to={to} aria-current={active === tabValue ? 'page' : undefined}>{label}</Link>
    </TabsTrigger>
  );

  return (
    <Tabs value={active} panels="external">
      <TabsList>
        {tab('installed', t('Active'), '/admin/threat-arsenal')}
        {tab('available', t('Available'), '/admin/threat-arsenal/available')}
      </TabsList>
    </Tabs>
  );
};

export default ThreatArsenalTabs;
