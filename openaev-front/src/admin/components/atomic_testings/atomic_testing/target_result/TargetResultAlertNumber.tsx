import { Button, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, type MouseEvent, useEffect, useState } from 'react';

import { getAlertLinksCount } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../../components/i18n';
import { type InjectExpectationResult } from '../../../../../utils/api-types';

interface Props {
  injectExpectationId: string;
  expectationResult: InjectExpectationResult;
  onShowAlerts?: () => void;
}

// Alert count for a security-platform result line. When alerts exist, renders
// a small primary button opening the alerts dialog; otherwise a plain "0".
const TargetResultAlertNumber: FunctionComponent<Props> = ({
  injectExpectationId,
  expectationResult,
  onShowAlerts,
}) => {
  const { t } = useFormatter();
  const [alertLinksNumber, setAlertLinksNumber] = useState<number | null>(0);

  useEffect(() => {
    getAlertLinksCount(injectExpectationId, expectationResult.sourceId, expectationResult.sourceType).then((result: { data: number }) => setAlertLinksNumber(result.data ?? 0));
  }, [injectExpectationId, expectationResult.sourceId]);

  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onShowAlerts?.();
  };

  if (!alertLinksNumber || !onShowAlerts) {
    return (
      <Typography sx={{
        fontSize: 13,
        fontVariantNumeric: 'tabular-nums',
      }}
      >
        {alertLinksNumber ?? 0}
      </Typography>
    );
  }

  return (
    <Tooltip title={t('Show alerts')}>
      <Button
        size="small"
        variant="contained"
        color="primary"
        disableElevation
        onClick={handleClick}
        sx={{
          minWidth: 32,
          height: 24,
          paddingInline: 1,
          fontSize: 12,
          fontWeight: 700,
          lineHeight: 1,
          borderRadius: 1,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {alertLinksNumber}
      </Button>
    </Tooltip>
  );
};

export default TargetResultAlertNumber;
