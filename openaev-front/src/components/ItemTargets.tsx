import { DevicesOtherOutlined, DnsOutlined, Groups3Outlined, SmartToyOutlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type TargetSimple } from '../utils/api-types';
import { getRemainingItemsCount, getVisibleItems, truncate } from '../utils/String';
import { useFormatter } from './i18n';

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
  tooltipTable: {
    'borderCollapse': 'collapse',
    '& th': {
      textAlign: 'left',
      textTransform: 'uppercase',
      fontSize: 10,
      fontWeight: 600,
      opacity: 0.7,
      padding: '2px 12px 4px 0',
    },
    '& td': {
      fontSize: 12,
      padding: '2px 12px 2px 0',
      verticalAlign: 'middle',
    },
  },
  tooltipName: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
  tooltipMore: {
    fontSize: 11,
    opacity: 0.7,
    marginTop: 4,
  },
}));

// Cap the rich tooltip so an inject targeting hundreds of assets stays readable.
const MAX_TOOLTIP_ROWS = 20;

const typeLabelKey = (type: string | undefined): string => {
  switch (type) {
    case 'AGENT':
    case 'AGENTS':
      return 'Agent';
    case 'ASSETS':
    case 'ENDPOINTS':
      return 'Asset';
    case 'ASSETS_GROUPS':
      return 'Asset group';
    case 'AI_TARGETS':
      return 'AI target';
    case 'PLAYERS':
      return 'Player';
    case 'TEAMS':
      return 'Team';
    case 'MANUAL':
      return 'Manual';
    default:
      return type ?? '-';
  }
};

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
  const { t } = useFormatter();
  let truncateLimit = 15;
  if (variant === 'reduced-view') {
    truncateLimit = 6;
  }

  // Extract the first two targets as visible chips
  const visibleTargets = getVisibleItems(targets, 1);
  const remainingTargets = targets?.slice(visibleTargets?.length ?? 0) ?? [];
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
    if (type === 'AI_TARGETS') {
      return <SmartToyOutlined style={{ fontSize: '1rem' }} />;
    }
    if (type === 'MANUAL') {
      return <DnsOutlined style={{ fontSize: '1rem' }} />;
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
        <Tooltip
          slotProps={{ tooltip: { sx: { maxWidth: 480 } } }}
          title={(
            <>
              <table className={classes.tooltipTable}>
                <thead>
                  <tr>
                    <th>{t('Name')}</th>
                    <th>{t('Type')}</th>
                  </tr>
                </thead>
                <tbody>
                  {remainingTargets.slice(0, MAX_TOOLTIP_ROWS).map(target => (
                    <tr key={target.target_id}>
                      <td>
                        <span className={classes.tooltipName}>
                          {getIcon(target.target_type!)}
                          {truncate(target.target_name ?? '-', 40)}
                        </span>
                      </td>
                      <td>{t(typeLabelKey(target.target_type))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {remainingTargets.length > MAX_TOOLTIP_ROWS && (
                <div className={classes.tooltipMore}>
                  {`+${remainingTargets.length - MAX_TOOLTIP_ROWS} ${t('more')}`}
                </div>
              )}
            </>
          )}
        >
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
