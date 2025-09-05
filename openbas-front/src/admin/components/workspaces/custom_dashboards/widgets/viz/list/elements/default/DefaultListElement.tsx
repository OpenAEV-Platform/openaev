import { Description } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText, Tooltip } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import useBodyItemsStyles from '../../../../../../../../../components/common/queryable/style/style';
import { type EsBase } from '../../../../../../../../../utils/api-types';
import buildStyles from '../ColumnStyles';
import DefaultElementStyles from './DefaultElementStyles';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

type Props = {
  columns: string[];
  element: EsBase;
};

const DefaultListElement = (props: Props) => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const elementsFromColumn = (column: string) => {
    // Cannot set a display name here
    // eslint-disable-next-line react/display-name
    return (element: EsBase) => {
      const key = column as keyof typeof element;
      const text = element[key]?.toString() || '';
      return (
        <Tooltip title={text} placement="bottom-start">
          <span>{text}</span>
        </Tooltip>
      );
    };
  };

  return (
    <ListItemButton classes={{ root: classes.item }} inert={true}>
      <ListItemIcon>
        <Description color="primary" />
      </ListItemIcon>
      <ListItemText
        primary={(
          <div style={bodyItemsStyles.bodyItems}>
            {props.columns.map(col => (
              <div
                key={col}
                style={{
                  ...bodyItemsStyles.bodyItem,
                  ...buildStyles(props.columns, DefaultElementStyles)[col],
                }}
              >
                {elementsFromColumn(col)(props.element)}
              </div>
            ))}
          </div>
        )}
      />
    </ListItemButton>
  );
};

DefaultListElement.displayName = 'DefaultListElement';

export default DefaultListElement;
