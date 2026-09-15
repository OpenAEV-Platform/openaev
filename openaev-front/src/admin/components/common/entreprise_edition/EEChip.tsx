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

  const chip = <Chip label={t('EE')} severity="ee" size="sm" onClick={onClick} style={style} />;

  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        {/* The trigger hands its own onClick to its child, which would turn a plain marker into a button. */}
        <TooltipTrigger asChild>
          {onClick ? chip : <span className="inline-flex">{chip}</span>}
        </TooltipTrigger>
        <TooltipContent>{t('Enterprise Edition Feature')}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default EEChip;
