import { useFormatter } from '../../../../../components/i18n';
import ItemStatus from '../../../../../components/ItemStatus';
import { type InjectStatus as InjectStatusType } from '../../../../../utils/api-types';
import { getInjectStatusLabel, getInjectStatusTooltip } from '../../../../../utils/statusLabels';

interface Props { status?: InjectStatusType['status_name'] }

const InjectStatus = ({ status }: Props) => {
  const { t } = useFormatter();

  return (
    <ItemStatus
      status={status}
      label={t(getInjectStatusLabel(status ?? 'Unknown'))}
      tooltipLabel={t(getInjectStatusTooltip(status ?? 'Unknown'))}
    />
  );
};

export default InjectStatus;
