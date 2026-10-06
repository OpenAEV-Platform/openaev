import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import { useFormatter } from '../../../components/i18n';
import { type PayloadSimple } from '../../../utils/api-types';

interface Props { status?: PayloadSimple['payload_status'] }

/**
 * Compact inline "Deprecated" chip shown next to injects whose contract payload
 * is deprecated (issue #3839). Renders nothing for any other status so callers
 * can pass the payload status unconditionally.
 */
const PayloadDeprecatedChip = ({ status }: Props) => {
  const { t } = useFormatter();

  if (status !== 'DEPRECATED') {
    return null;
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip label={t('Deprecated')} style={{ flexShrink: 0 }} />
      </TooltipTrigger>
      <TooltipContent>{t('Deprecated: Functionality not guaranteed')}</TooltipContent>
    </Tooltip>
  );
};

export default PayloadDeprecatedChip;
