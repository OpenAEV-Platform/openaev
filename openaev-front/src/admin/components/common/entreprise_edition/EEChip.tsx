import { Chip, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { type CSSProperties, type MouseEvent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';

const EEChip = ({ clickable = false, featureDetectedInfo = null, style = {} }: {
  clickable?: boolean;
  featureDetectedInfo?: string | null;
  style?: CSSProperties;
}) => {
  const { t } = useFormatter();
  const { isValidated: isEnterpriseEdition, openDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  if (featureDetectedInfo) {
    setEEFeatureDetectedInfo(featureDetectedInfo);
  }
  const onClick = clickable && !isEnterpriseEdition
    ? (event: MouseEvent<HTMLElement>) => {
        event.preventDefault();
        event.stopPropagation();
        openDialog();
      }
    : undefined;

  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <Chip label={t('EE')} severity="ee" size="sm" onClick={onClick} style={style} />
        </TooltipTrigger>
        <TooltipContent>{t('Enterprise Edition Feature')}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default EEChip;
