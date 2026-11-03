import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';

interface Props { status?: PayloadSimple['payload_status'] | null }

// Filigran's verification of a payload, shown as a tag: it does not decide whether it can be used.
const PayloadVerificationTag: FunctionComponent<Props> = ({ status }) => {
  const { t } = useFormatter();
  if (status !== 'VERIFIED' && status !== 'DEPRECATED') {
    return null;
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="inline-flex">
          <Chip label={t(status === 'VERIFIED' ? 'Filigran verified' : 'Deprecated')} />
        </span>
      </TooltipTrigger>
      <TooltipContent>
        {t(status === 'VERIFIED' ? 'Verified and tested by Filigran' : 'Deprecated: Functionality not guaranteed')}
      </TooltipContent>
    </Tooltip>
  );
};

export default PayloadVerificationTag;
