import { type FunctionComponent, useEffect, useState } from 'react';

import { searchThreatArsenalActions } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import MarketplaceHero from '../../../../components/common/marketplace/page/MarketplaceHero';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateThreatArsenalAction from '../CreateThreatArsenalAction';

interface Props { onCreate: (action: ThreatArsenalAction) => void }

const ThreatArsenalHero: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  // The whole arsenal, never the current page or filters.
  const [active, setActive] = useState(0);
  useEffect(() => {
    searchThreatArsenalActions(buildSearchPagination({ size: 1 }))
      .then(({ data }: { data: { totalElements?: number } }) => setActive(data?.totalElements ?? 0))
      .catch(() => undefined);
  }, []);

  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse and version the actions your simulations run.')}
      stats={[{
        id: 'active',
        label: t('Active'),
        value: active,
        icon: 'layers-2',
        to: '/admin/threat-arsenal',
      }]}
      action={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <CreateThreatArsenalAction onCreate={onCreate} />
        </Can>
      )}
    />
  );
};

export default ThreatArsenalHero;
