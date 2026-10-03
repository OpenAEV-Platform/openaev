import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import * as PropTypes from 'prop-types';

import { truncate } from '../utils/String';

const TruncatedText = (props) => {
  const { content, limit } = props;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>{truncate(content, limit)}</span>
      </TooltipTrigger>
      {content && <TooltipContent>{content}</TooltipContent>}
    </Tooltip>
  );
};

TruncatedText.propTypes = {
  content: PropTypes.string,
  limit: PropTypes.number,
};

export default TruncatedText;
