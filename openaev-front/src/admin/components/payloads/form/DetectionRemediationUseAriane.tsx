import { Button, SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useAI from '../../../../utils/hooks/useAI';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { isNotEmptyField } from '../../../../utils/utils';
import EEChip from '../../common/entreprise_edition/EEChip';
import EETooltip from '../../common/entreprise_edition/EETooltip';
import useIsEligibleArianeCollector from '../hook/useIsEligibleArianeCollector';
import useIsEligibleArianePayloadType from '../hook/useIsEligibleArianePayloadType';
import Loader from '../Loader';
import { useSnapshotRemediation } from '../utils/useSnapshotRemediation';

export interface Props {
  collectorType: string;
  payloadType?: string | undefined;
  detectionRemediationContent?: string;
  onSubmit: () => Promise<void>;
  isValidForm?: boolean;
}

const DetectionRemediationUseAriane = ({
  collectorType,
  payloadType,
  detectionRemediationContent,
  onSubmit,
  isValidForm = true,
}: Props) => {
  const { snapshot } = useSnapshotRemediation();
  const { t } = useFormatter();
  // Fetch data
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { enabled, configured } = useAI();
  const isAvailable = isEnterpriseEdition && enabled && configured;

  const [loading, setLoading] = useState(false);
  const isEligibleArianeCollector = useIsEligibleArianeCollector(collectorType);
  const isEligibleArianePayload = useIsEligibleArianePayloadType(payloadType);
  const hasContent = isNotEmptyField(detectionRemediationContent);

  const handleClick = async () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Ariane AI'));
      openEnterpriseEditionDialog();
    } else {
      setLoading(true);
      onSubmit().finally(() => setLoading(false));
    }
  };

  let btnLabel = t('Ask AI');
  if (!isAvailable) {
    btnLabel = btnLabel + ' (EE)';
  }
  if (!isEligibleArianeCollector) {
    btnLabel = btnLabel + t(' is not available for current collector');
  } else if (!isEligibleArianePayload) {
    btnLabel = btnLabel + t(' is not available for current payload type');
  } else if (!isValidForm) {
    btnLabel = btnLabel + t(' is locked until required fields are filled.');
  } else if (hasContent) {
    btnLabel = btnLabel + t(' is only available for empty content');
  }

  const disabled = !isEligibleArianeCollector || !isAvailable || hasContent || !isValidForm || !isEligibleArianePayload;

  return (
    <EETooltip forAi title={btnLabel}>
      <span>
        {(loading || snapshot?.get(collectorType)?.isLoading) ? (
          <Loader />
        ) : (
          <Button
            type="button"
            variant="outlined"
            sx={{
              marginLeft: 'auto',
              color: isEnterpriseEdition ? 'ai.main' : 'action.disabled',
              borderColor: isEnterpriseEdition ? 'ai.main' : 'action.disabledBackground',
            }}
            size="small"
            onClick={handleClick}
            startIcon={<SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />}
            endIcon={isEnterpriseEdition ? <></> : <span><EEChip /></span>}
            disabled={disabled || loading}
          >
            {t('Use Ariane ')}
          </Button>
        )}
      </span>
    </EETooltip>
  );
};
export default DetectionRemediationUseAriane;
