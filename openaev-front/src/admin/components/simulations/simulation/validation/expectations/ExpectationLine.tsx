import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import ResultChip from './ResultChip';

const useStyles = makeStyles()(theme => ({
  item: { height: 40 },
  container: {
    display: 'flex',
    placeContent: 'space-between',
    fontSize: theme.typography.h3.fontSize,
  },
  chip: {
    display: 'flex',
    gap: theme.spacing(2),
  },
  details: { display: 'flex' },
}));

interface Props {
  expectation: InjectExpectationsStore;
  info?: string;
  title: string;
  icon: ReactElement;
  onClick?: () => void;
  gap?: number;
}

const ExpectationLine: FunctionComponent<Props> = ({
  expectation,
  info,
  title,
  icon,
  onClick,
  gap,
}) => {
  // Standard hooks
  const { classes } = useStyles();

  return (
    <>
      <ListItemButton
        key={expectation.inject_expectation_id}
        divider
        sx={{ pl: gap ?? 8 }}
        classes={{ root: classes.item }}
        onClick={onClick}
      >
        <ListItemIcon>
          {icon}
        </ListItemIcon>
        <ListItemText
          primary={(
            <div className={classes.container}>
              <div className={classes.details}>
                <div style={{ width: gap ? 135 : 200 }}>
                  {' '}
                  {info}
                  {' '}
                </div>
                {title}
              </div>
              <div className={classes.chip}>
                <ResultChip expectation={expectation} />
              </div>
            </div>
          )}
        />
      </ListItemButton>
    </>
  );
};

export default ExpectationLine;
