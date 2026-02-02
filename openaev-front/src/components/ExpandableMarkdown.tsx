import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { emptyFilled, truncate } from '../utils/String';
import MarkdownDisplay from './MarkdownDisplay';

interface ExpandableMarkdownProps {
  source?: string | null;
  limit?: number;
  showAll?: boolean;
  markdownDOMId?: string;
}

const ExpandableMarkdown: FunctionComponent<ExpandableMarkdownProps> = ({
  source,
  limit = 500,
  showAll = false,
  markdownDOMId = '',
}) => {
  const [expand, setExpand] = useState(showAll);
  const onClick = () => setExpand(!expand);
  const shouldBeTruncated = !showAll && (source || '').length > limit;
  return (
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
      <div id={markdownDOMId} style={{ overflowX: 'auto' }}>
        <MarkdownDisplay
          content={expand ? emptyFilled(source) : (truncate(source, limit) ?? null)}
          remarkGfmPlugin={true}
          commonmark={true}
        />
      </div>
    </div>
  );
};

export default ExpandableMarkdown;
