import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import { useFormatter } from '../../../i18n';

const DateFragment = ({ value }: { value: string }) => {
  const { nsdt } = useFormatter();
  const formattedDate = nsdt(value);

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>{formattedDate}</span>
      </TooltipTrigger>
      {formattedDate && <TooltipContent side="bottom" align="start">{formattedDate}</TooltipContent>}
    </Tooltip>
  );
};

export default DateFragment;
