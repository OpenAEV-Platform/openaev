import { useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import Tag from '../../../../components/common/tag/Tag';
import { useFormatter } from '../../../../components/i18n';

interface Props { deletedAt?: string }

const TenantStatus: FunctionComponent<Props> = ({ deletedAt }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <Tag
      label={deletedAt ? t('Deleted') : t('Active')}
      color={deletedAt ? theme.palette.error.main : theme.palette.success.main}
      labelTextTransform="uppercase"
      maxWidth={120}
    />
  );
};

export default TenantStatus;
