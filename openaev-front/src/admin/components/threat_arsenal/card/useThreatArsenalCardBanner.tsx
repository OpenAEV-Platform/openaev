import { CardBanner } from '@filigran/design-system';
import { type MouseEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import useCountLabel from '../../../../utils/hooks/useCountLabel';
import { type OpenedTab } from '../hook/useOpenedAction';
import { isShownRevoked } from '../utils/threatArsenalStatusUtils';
import useOlderUsageCount from './useOlderUsageCount';

/** The card's one banner, a direct child of the Card: a revoked version first, then injects left on an older version. */
const useThreatArsenalCardBanner = (
  action: ThreatArsenalAction,
  olderVersionsInUse: boolean,
  onShowVersions: (action: ThreatArsenalAction, tab?: OpenedTab) => void,
) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  // Read on every render, before any condition: it reads the feature flags, a hook.
  const revoked = isShownRevoked(action);
  // How many, once counted: the size of the job says whether to move now.
  const olderCount = useOlderUsageCount(action.injector_contract_id, olderVersionsInUse && !revoked);
  // The card itself opens the drawer on click: the banner action opens its Versions tab instead.
  const showVersions = (tab: OpenedTab) => (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    onShowVersions(action, tab);
  };

  if (revoked) {
    return (
      <CardBanner
        severity="error"
        icon="ban"
        action={{
          label: t('Versions'),
          onClick: showVersions('versions'),
        }}
      >
        {t('Revoked: injects using it fail')}
      </CardBanner>
    );
  }
  if (olderVersionsInUse) {
    return (
      <CardBanner
        severity="warning"
        action={{
          label: t('Move'),
          onClick: showVersions('move'),
        }}
      >
        {olderCount ? counted(olderCount, '1 inject uses an older version', '{count} injects use an older version') : t('Injects use an older version')}
      </CardBanner>
    );
  }
  return null;
};

export default useThreatArsenalCardBanner;
