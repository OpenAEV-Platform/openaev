import { IconButton } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { type FunctionComponent, useState } from 'react';

import { truncate } from '../../utils/String';
import { useFormatter } from '../i18n';

interface Props {
  source: string | undefined;
  limit?: number;
}

const ExpandableText: FunctionComponent<Props> = ({
  source,
  limit = 22,
}) => {
  const { t } = useFormatter();
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
            <IconButton
              icon={expand ? <ExpandLess /> : <ExpandMore />}
              aria-label={expand ? t('Collapse') : t('Expand')}
              onClick={onClick}
              priority="tertiary"
              size="md"
            />
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
