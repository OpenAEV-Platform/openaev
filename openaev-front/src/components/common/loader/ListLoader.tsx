import { IconButton } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { ListItem, ListItemIcon, ListItemText, Skeleton, type SvgIconProps } from '@mui/material';
import { type ComponentType, type CSSProperties, type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';
import { type Header } from '../SortHeadersList';

interface Props {
  headers: Header[];
  headerStyles: Record<string, CSSProperties>;
  Icon: ComponentType<SvgIconProps>;
  height?: number;
  number?: number;
}

const ListLoader: FunctionComponent<Props> = ({
  headers,
  headerStyles,
  Icon,
  height = 50,
  number = 1,
}) => {
  const { t } = useFormatter();
  return (
    [...Array(number)].map((_, key) => (
      <ListItem
        key={key}
        divider
        style={{
          height,
          pointerEvents: 'none',
        }}
        secondaryAction={(
          <IconButton
            icon={<MoreVert fontSize="small" />}
            aria-label={t('More actions')}
            disabled
            priority="tertiary"
            size="md"
          />
        )}
      >
        <ListItemIcon>
          <Icon color="disabled" />
        </ListItemIcon>
        <ListItemText
          primary={(
            <div style={{ display: 'flex' }}>
              {headers.map(header => (
                <div
                  key={header.field}
                  style={{
                    ...headerStyles[header.field],
                    paddingRight: 10,
                  }}
                >
                  <Skeleton height={40} />
                </div>
              ))}
            </div>
          )}
        />
      </ListItem>
    ))
  );
};

export default ListLoader;
