import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import { type InjectStatus as InjectStatusType } from '../../../../../utils/api-types';
import { getInjectStatusLabel, getInjectStatusTooltip } from '../../../../../utils/statusLabels';

interface Props {
  status?: InjectStatusType['status_name'];
  /** Actual failure reason (from the global execution traces); shown instead of the generic tooltip. */
  errorMessage?: string;
}

const InjectStatus = ({ status, errorMessage }: Props) => {
  const { t } = useFormatter();
  // When we have the concrete failure reason, show it instead of the generic
  // "the inject could not be completed" boilerplate so the cause is readable
  // straight from the status chip.
  const tooltipLabel = errorMessage?.trim()
    ? errorMessage
    : t(getInjectStatusTooltip(status ?? 'Unknown'));

  return (
    <ItemStatus
      status={status}
      label={t(getInjectStatusLabel(status ?? 'Unknown'))}
      tooltipLabel={tooltipLabel}
    />
  );
};

export default InjectStatus;
