import { Tooltip } from '@mui/material';

import { useFormatter } from '../../../i18n';

const DateFragment = ({ value }: { value: string }) => {
  const { nsdt } = useFormatter();
  const formattedDate = nsdt(value);

  return (
    <Tooltip title={formattedDate} placement="bottom-start">
      <span>{formattedDate}</span>
    </Tooltip>
  );
};

export default DateFragment;
