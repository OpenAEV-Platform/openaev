import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type IocValidationOutcome, iocValidationOutcomeLabel, iocValidationOutcomeSeverity } from './iocValidationUtils';

interface Props { outcome?: IocValidationOutcome }

const IocValidationOutcomeChip: FunctionComponent<Props> = ({ outcome }) => {
  const { t } = useFormatter();
  return (
    <Chip
      label={t(iocValidationOutcomeLabel(outcome))}
      severity={iocValidationOutcomeSeverity(outcome)}
      style={{ maxWidth: '100%' }}
    />
  );
};

export default IocValidationOutcomeChip;
