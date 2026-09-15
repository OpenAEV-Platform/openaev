import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import AssetStatus from '../../../../admin/components/assets/AssetStatus';
import { getActiveMsgTooltip } from '../../../../utils/endpoints/utils';
import { useFormatter } from '../../../i18n';

type Props = { activity_map?: boolean[] };

const EndpointActiveFragment = (props: Props) => {
  const { t } = useFormatter();
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
