import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../i18n';
import Tag from './Tag';

interface Props { tooltip?: string }

const DangerZone = ({ tooltip }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Tag
      data-testid="danger-zone"
      label={t('Danger Zone')}
      color={theme.palette.error.main}
      tooltipTitle={tooltip}
      disableTooltip={!tooltip}
      labelTextTransform="none"
    />
  );
};

export default DangerZone;
