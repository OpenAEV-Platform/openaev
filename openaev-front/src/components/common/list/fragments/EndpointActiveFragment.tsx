import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import AssetStatus from '../../../../admin/components/assets/AssetStatus';
import { type EndpointOutput } from '../../../../utils/api-types';
import { getActiveMsgTooltip } from '../../../../utils/endpoints/utils';
import { useFormatter } from '../../../i18n';

type Props = {
  activity_map?: boolean[];
  // The server's status: STALE (unseen past the tenant threshold) wins over the agents' activity.
  asset_status?: EndpointOutput['asset_status'];
  asset_last_seen?: string;
  // The tenant threshold in days, when the caller could read it.
  stale_after_days?: number;
};

const EndpointActiveFragment = (props: Props) => {
  const { t, fldt } = useFormatter();
  if (props.asset_status === 'STALE') {
    const lastSeen = props.asset_last_seen ? fldt(props.asset_last_seen) : '-';
    let tooltip = t('Last seen {date}', { date: lastSeen });
    if (props.stale_after_days === 1) {
      tooltip = t('Last seen {date}, threshold 1 day', { date: lastSeen });
    } else if (props.stale_after_days) {
      tooltip = t('Last seen {date}, threshold {days} days', {
        date: lastSeen,
        days: String(props.stale_after_days),
      });
    }
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            <AssetStatus variant="list" status="Stale" />
          </span>
        </TooltipTrigger>
        <TooltipContent>{tooltip}</TooltipContent>
      </Tooltip>
    );
  }
  const status = getActiveMsgTooltip(props.activity_map ?? [], t('Active'), t('Inactive'), t('Agentless'));
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>
          <AssetStatus variant="list" status={status.status} />
        </span>
      </TooltipTrigger>
      {status.activeMsgTooltip && <TooltipContent>{status.activeMsgTooltip}</TooltipContent>}
    </Tooltip>
  );
};

export default EndpointActiveFragment;
