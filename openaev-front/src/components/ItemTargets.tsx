import { DevicesOtherOutlined, Groups3Outlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type TargetSimple } from '../utils/api-types';
import { getLabelOfRemainingItems, getRemainingItemsCount, getVisibleItems, truncate } from '../utils/String';

const useStyles = makeStyles()(theme => ({
  inline: { display: 'flex' },
  target: {
    fontSize: 12,
    height: 20,
    float: 'left',
    marginRight: 4,
    borderRadius: 4,
  },
  clickable: {
    'cursor': 'pointer',
    '&:hover': {
      borderColor: theme.palette.primary.main,
      color: theme.palette.primary.main,
    },
  },
}));

interface Props {
  targets: TargetSimple[] | undefined;
  variant?: string;
  // When provided, each target chip becomes a clickable link to the resolved
  // URL (used e.g. to pivot from a finding to its impacted endpoint). Returning
  // undefined keeps that individual chip non-clickable.
  getTargetLink?: (target: TargetSimple) => string | undefined;
}

const ItemTargets: FunctionComponent<Props> = ({
  targets,
  variant,
  getTargetLink,
}) => {
  // Standard hooks
  const { classes, cx } = useStyles();
  let truncateLimit = 15;
  if (variant === 'reduced-view') {
    truncateLimit = 6;
  }

  // Extract the first two targets as visible chips
  const visibleTargets = getVisibleItems(targets, 1);
  const tooltipLabel = getLabelOfRemainingItems(targets, 1, 'target_name');
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
      {visibleTargets && visibleTargets.map((target: TargetSimple, index: number) => {
        const link = getTargetLink?.(target);
        return (
          <span key={index}>
            <Tooltip title={target.target_name}>
              <Chip
                variant="outlined"
                key={target.target_id}
                classes={{ root: link ? cx(classes.target, classes.clickable) : classes.target }}
                icon={getIcon(target.target_type!)}
                label={truncate(target.target_name!, truncateLimit)}
                {...(link
                  ? {
                      component: Link,
                      to: link,
                      clickable: true,
                    }
                  : {})}
              />
            </Tooltip>
          </span>
        );
      })}
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
