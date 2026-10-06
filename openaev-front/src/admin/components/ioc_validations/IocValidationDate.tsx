import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import moment from 'moment-timezone';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { IOC_VALIDATION_FOCUS_RING_CLASS } from './iocValidationUtils';

interface Props { date: string }

const IocValidationDate: FunctionComponent<Props> = ({ date }) => {
  const { fldt } = useFormatter();
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <time dateTime={date} tabIndex={0} className={IOC_VALIDATION_FOCUS_RING_CLASS}>{moment(date).fromNow()}</time>
      </TooltipTrigger>
      <TooltipContent>{fldt(date)}</TooltipContent>
    </Tooltip>
  );
};

export default IocValidationDate;
