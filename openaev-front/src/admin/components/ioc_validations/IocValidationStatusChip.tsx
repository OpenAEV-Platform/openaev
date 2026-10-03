import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type IocValidationStatus, iocValidationStatusLabel, iocValidationStatusSeverity } from './iocValidationUtils';

interface Props { status: IocValidationStatus }

const IocValidationStatusChip: FunctionComponent<Props> = ({ status }) => {
  const { t } = useFormatter();
  return (
    <Chip
      label={t(iocValidationStatusLabel(status))}
      severity={iocValidationStatusSeverity(status)}
      style={{ maxWidth: '100%' }}
    />
  );
};

export default IocValidationStatusChip;
