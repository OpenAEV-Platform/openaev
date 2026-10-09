import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

export type ThreatArsenalTab = 'installed' | 'available';

interface Props { active: ThreatArsenalTab }

const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();
  return (
    <Tabs value={active} panels="external">
      <TabsList>
        <TabsTrigger value="installed" asChild>
          <Link to="/admin/threat-arsenal" aria-current={active === 'installed' ? 'page' : undefined}>{t('Installed')}</Link>
        </TabsTrigger>
        <TabsTrigger value="available" asChild>
          <Link to="/admin/threat-arsenal/available" aria-current={active === 'available' ? 'page' : undefined}>{t('Available')}</Link>
        </TabsTrigger>
      </TabsList>
    </Tabs>
  );
};

export default ThreatArsenalTabs;
