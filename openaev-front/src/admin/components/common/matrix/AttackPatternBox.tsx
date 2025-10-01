import { Button, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent as ReactMouseEvent, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackPattern, type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern, type InjectExpectationResultsByType } from '../../../../utils/api-types';
import { hexToRGB } from '../../../../utils/Colors';
import AtomicTestingResult from '../../atomic_testings/atomic_testing/AtomicTestingResult';
import { type ExpectationResultType, mitreMatrixExpectationTypes } from '../injects/expectations/Expectation';

const useStyles = makeStyles()(theme => ({
  button: {
    whiteSpace: 'nowrap',
    width: '100%',
    textTransform: 'capitalize',
    color: theme.palette.text?.primary,
    backgroundColor: theme.palette.background.accent,
    borderRadius: 4,
  },
  buttonDummy: {
    whiteSpace: 'nowrap',
    width: '100%',
    textTransform: 'capitalize',
    color: theme.palette.text?.primary,
    backgroundColor: hexToRGB(theme.palette.background.accent, 0.4),
    borderRadius: 4,
  },
  buttonText: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
    padding: theme.spacing(0.2),
    width: '100%',
  },
}));

interface AttackPatternBoxProps {
  goToLink?: string;
  attackPattern: AttackPattern;
  injectResult: InjectExpectationResultsByAttackPattern | undefined;
  dummy?: boolean;
}

const AttackPatternBox: FunctionComponent<AttackPatternBoxProps> = ({
  goToLink,
  attackPattern,
  injectResult,
  dummy,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const [open, setOpen] = useState<boolean>(false);
  const [anchorEl, setAnchorEl] = useState<Element | null>(null);
  const results: InjectExpectationResultsByType[] = injectResult?.inject_expectation_results ?? [];

  if (dummy) {
    const content = () => (
      <div className={classes.buttonText}>
        <Typography variant="caption" style={{ color: theme.palette.text?.disabled }}>
          {attackPattern.attack_pattern_name}
        </Typography>
        <AtomicTestingResult expectations={results[0]?.results ?? []} />
      </div>
    );
    return (
      <div
        key={attackPattern.attack_pattern_id}
        className={classes.buttonDummy}
      >
        {content()}
      </div>
    );
  }
  const handleOpen = (event: ReactMouseEvent<HTMLButtonElement, MouseEvent>) => {
    event.stopPropagation();
    setOpen(true);
    setAnchorEl(event.currentTarget);
  };
  const lowestSelector = (aggregation: (('FAILED' | 'PENDING' | 'PARTIAL' | 'UNKNOWN' | 'SUCCESS' | undefined)[])): 'FAILED' | 'PENDING' | 'PARTIAL' | 'UNKNOWN' | 'SUCCESS' => {
    if (aggregation.includes('FAILED')) {
      return 'FAILED';
    }
    if (aggregation.includes('PARTIAL')) {
      return 'PARTIAL';
    }
    if (aggregation.includes('PENDING')) {
      return 'PENDING';
    }
    if (aggregation.includes('UNKNOWN')) {
      return 'UNKNOWN';
    }
    return 'SUCCESS';
  };

  const buildAggregate = (type: ExpectationResultType): ExpectationResultsByType | null => {
    const filtered = results
      .map(r => r.results?.filter(er => er.type === type).map(er => er.avgResult))
      .flat()
      .filter(Boolean);

    if (filtered.length === 0) return null;

    return {
      type,
      avgResult: lowestSelector(filtered),
      distribution: [],
    };
  };

  const aggregatedResults: ExpectationResultsByType[] = mitreMatrixExpectationTypes
    .map(buildAggregate)
    .filter((agg): agg is ExpectationResultsByType => agg !== null);

  return (
    <>
      <Button
        aria-haspopup="true"
        aria-expanded={open ? 'true' : undefined}
        className={classes.button}
        onClick={event => handleOpen(event)}
      >
        <div className={classes.buttonText}>
          <Typography variant="caption">
            {attackPattern.attack_pattern_name}
          </Typography>
          <AtomicTestingResult expectations={aggregatedResults} />
        </div>
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={() => {
          setAnchorEl(null);
          setOpen(false);
        }}
        anchorOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'left',
        }}
      >
        {results?.map((result, idx) => {
          const content = () => (
            <>
              <ListItemText primary={result.inject_title} />
              <AtomicTestingResult expectations={result.results ?? []} />
            </>
          );
          if (goToLink) {
            return (
              <MenuItem
                key={`inject-result-${idx}`}
                component={Link}
                to={goToLink + '/' + result.inject_id}
                style={{
                  display: 'flex',
                  gap: 8,
                }}
              >
                {content()}
              </MenuItem>
            );
          }
          return (
            <MenuItem
              key={`inject-result-${idx}`}
              style={{
                display: 'flex',
                gap: 8,
                pointerEvents: 'none',
              }}
            >
              {content()}
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
};

export default AttackPatternBox;
