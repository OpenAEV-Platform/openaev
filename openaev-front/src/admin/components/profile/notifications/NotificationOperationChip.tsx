import { Chip } from '@filigran/design-system';

import { useFormatter } from '../../../../components/i18n';
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
    <Chip label={t(operationLabel(operation))} color={color} />
  );
};

export default NotificationOperationChip;
