import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import moment from 'moment-timezone';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { IOC_VALIDATION_FOCUS_RING_CLASS } from './iocValidationUtils';

interface Props {
  date: string;
  // Off inside a link (a list row): a focus stop there would follow the link when activated.
  focusable?: boolean;
}

const IocValidationDate: FunctionComponent<Props> = ({ date, focusable = true }) => {
  const { fldt } = useFormatter();
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <time
          dateTime={date}
          tabIndex={focusable ? 0 : undefined}
          className={focusable ? IOC_VALIDATION_FOCUS_RING_CLASS : undefined}
        >
          {moment(date).fromNow()}
        </time>
      </TooltipTrigger>
      <TooltipContent>{fldt(date)}</TooltipContent>
    </Tooltip>
  );
};

export default IocValidationDate;
