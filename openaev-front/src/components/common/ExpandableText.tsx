import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { truncate } from '../../utils/String';

interface Props {
  source: string | undefined;
  limit?: number;
}

const ExpandableText: FunctionComponent<Props> = ({
  source,
  limit = 22,
}) => {
  const [expand, setExpand] = useState(false);
  const onClick = () => setExpand(!expand);
  const shouldBeTruncated = (source || '').length > limit;
  return (
    <span>
      <div style={{ position: 'relative' }}>
        {shouldBeTruncated && (
          <div style={{
            position: 'absolute',
            top: -32,
            right: 0,
          }}
          >
            <IconButton onClick={onClick} size="large">
              {expand ? <ExpandLess /> : <ExpandMore />}
            </IconButton>
          </div>
        )}
        <div>
          {expand ? (source || '-') : truncate(source || '-', limit)}
        </div>
        <div className="clearfix" />
      </div>
    </span>
  );
};

export default ExpandableText;
