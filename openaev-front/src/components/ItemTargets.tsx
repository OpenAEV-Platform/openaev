import { DevicesOtherOutlined, Groups3Outlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type TargetSimple } from '../utils/api-types';
import { getLabelOfRemainingItems, getRemainingItemsCount, getVisibleItems, truncate } from '../utils/String';

const useStyles = makeStyles()(() => ({
  inline: { display: 'inline-block' },
  target: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 4,
    borderRadius: 4,
  },
}));

interface Props {
  targets: TargetSimple[] | undefined;
  variant?: string;
}

const ItemTargets: FunctionComponent<Props> = ({
  targets,
  variant,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  let truncateLimit = 15;
  if (variant === 'reduced-view') {
    truncateLimit = 6;
  }

  // Extract the first two targets as visible chips
  const visibleTargets = getVisibleItems(targets, 2);
  const tooltipLabel = getLabelOfRemainingItems(targets, 2, 'target_name');
  const remainingTargetsCount = getRemainingItemsCount(targets, visibleTargets);

  if (!targets || targets.length === 0) {
    return '-';
  }

  const getIcon = (type: string) => {
    if (type === 'ASSETS') {
      return <DevicesOtherOutlined style={{ fontSize: '1rem' }} />;
    }
    if (type === 'ASSETS_GROUPS') {
      return <SelectGroup style={{ fontSize: '1rem' }} />;
    }
    return <Groups3Outlined style={{ fontSize: '1rem' }} />; // Teams
  };

  return (
    <div className={classes.inline}>
      {visibleTargets && visibleTargets.map((target: TargetSimple, index: number) => (
        <span key={index}>
          <Tooltip title={target.target_name}>
            <Chip
              variant="outlined"
              key={target.target_id}
              classes={{ root: classes.target }}
              icon={getIcon(target.target_type!)}
              label={truncate(target.target_name!, truncateLimit)}
            />
          </Tooltip>
        </span>
      ))}
      {remainingTargetsCount && remainingTargetsCount > 0 && (
        <Tooltip title={tooltipLabel}>
          <Chip
            variant="outlined"
            classes={{ root: classes.target }}
            label={`+${remainingTargetsCount}`}
          />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemTargets;
