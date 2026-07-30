import { Chip } from '@mui/material';

import { useFormatter } from '../../../../components/i18n';
import { hexToRGB } from '../../../../utils/Colors';
import { operationColor, operationLabel } from './notificationUtils';

/**
 * The colored, uppercase operation chip of the notifications list (mirrors
 * OpenCTI's operation column: tinted background + solid border in the
 * operation color).
 */
const NotificationOperationChip = ({ operation }: { operation: string }) => {
  const { t } = useFormatter();
  const color = operationColor(operation);
  return (
    <Chip
      style={{
        fontSize: 12,
        height: 20,
        width: 150,
        textTransform: 'uppercase',
        borderRadius: 4,
        backgroundColor: hexToRGB(color, 0.08),
        color,
        border: `1px solid ${color}`,
      }}
      label={t(operationLabel(operation))}
    />
  );
};

export default NotificationOperationChip;
