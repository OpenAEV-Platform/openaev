import { Tooltip } from '@mui/material';

import AssetStatus from '../../../../admin/components/assets/AssetStatus';
import { getActiveMsgTooltip } from '../../../../utils/endpoints/utils';
import { useFormatter } from '../../../i18n';

type Props = { activity_map?: boolean[] };

const EndpointActiveFragment = (props: Props) => {
  const { t } = useFormatter();
  const status = getActiveMsgTooltip(props.activity_map ?? [], t('Active'), t('Inactive'), t('Agentless'));
  return (
    <Tooltip title={status.activeMsgTooltip}>
      <span>
        <AssetStatus variant="list" status={status.status} />
      </span>
    </Tooltip>
  );
};

export default EndpointActiveFragment;
