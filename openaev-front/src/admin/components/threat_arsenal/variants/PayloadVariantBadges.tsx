import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadSimple } from '../../../../utils/api-types';

interface Props { payload?: PayloadSimple }

// Customized catalogue payload: its base, a conflict to resolve.
const PayloadVariantBadges: FunctionComponent<Props> = ({ payload }) => {
  const { t } = useFormatter();
  if (!payload?.payload_variant) {
    return null;
  }
  return (
    <>
      {payload.payload_base_version
        ? (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex">
                  <Chip label={t('Customized')} />
                </span>
              </TooltipTrigger>
              <TooltipContent>{t('Based on v{version}', { version: payload.payload_base_version })}</TooltipContent>
            </Tooltip>
          )
        : <Chip label={t('Customized')} />}
      {payload.payload_conflict && <Chip label={t('Conflict')} severity="high" />}
    </>
  );
};

export default PayloadVariantBadges;
