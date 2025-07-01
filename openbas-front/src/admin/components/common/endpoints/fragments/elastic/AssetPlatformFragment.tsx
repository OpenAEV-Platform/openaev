import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../../../components/i18n';
import PlatformIcon from '../../../../../../components/PlatformIcon';
import { type EsEndpoint } from '../../../../../../utils/api-types';

type Props = { endpoint: EsEndpoint };

const AssetPlatformFragment = (props: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <>
      <PlatformIcon
        platform={props.endpoint.endpoint_platform ?? 'Unknown'}
        width={20}
        marginRight={theme.spacing(2)}
      />
      {props.endpoint.endpoint_platform ?? t('Unknown')}
    </>
  );
};

export default AssetPlatformFragment;
