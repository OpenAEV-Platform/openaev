import { Icon, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

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
    // The cause hangs on a focusable info icon beside the chip, never on a Tooltip around the
    // Chip: the design system Chip wraps itself in its own tooltip, which swallows an outer one.
    return (
      <span className="inline-flex items-center gap-1">
        <AssetStatus variant="list" status="Stale" />
        <Tooltip>
          <TooltipTrigger asChild>
            <span
              tabIndex={0}
              role="img"
              aria-label={tooltip}
              className="inline-flex rounded-sm text-icon-highlight focus-visible:outline-none focus-visible:ring-2 ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-focus"
              style={{ cursor: 'help' }}
            >
              <Icon name="info" size={16} aria-hidden />
            </span>
          </TooltipTrigger>
          <TooltipContent>{tooltip}</TooltipContent>
        </Tooltip>
      </span>
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
