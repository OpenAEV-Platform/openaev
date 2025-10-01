import { KeyboardArrowRightOutlined } from '@mui/icons-material';
import { Alert, Button, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type Contract } from '../../../../../../actions/contract/contract';
import Drawer from '../../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../../components/i18n';
import { type InjectExpectationResult } from '../../../../../../utils/api-types';
import { truncate } from '../../../../../../utils/String';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import { typeIcon } from '../../../../common/injects/expectations/ExpectationUtils';
import ExpectationLine from './ExpectationLine';

const useStyles = makeStyles()(theme => ({
  buttons: {
    display: 'flex',
    placeContent: 'space-between',
    gap: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
  message: {
    width: '100%',
    color: theme.palette.chip.main,
  },
  marginBottom_2: { marginBottom: theme.spacing(2) },
}));

interface Props {
  expectation: InjectExpectationsStore;
  injectContract: Contract;
  gap?: number;
}

const TechnicalExpectationAsset: FunctionComponent<Props> = ({
  expectation,
  injectContract,
  gap,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<InjectExpectationResult | null>(null);

  const toJsonFormat = (result: string) => {
    try {
      return JSON.parse(result).map((entry: string[], idx: number) => (
        <p key={idx}>{Object.entries(entry).map(([key, value]) => `${key}: ${value}\n`)}</p>
      ));
    } catch {
      return (<p>{result}</p>);
    }
  };

  return (
    <>
      <ExpectationLine
        expectation={expectation}
        info={injectContract.config.label?.en}
        title={injectContract.label.en}
        icon={typeIcon(expectation.inject_expectation_type)}
        onClick={() => setOpen(true)}
        gap={gap}
      />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Expectations of ') + injectContract.label.en}
      >
        <>
          <Alert
            classes={{ message: classes.message }}
            severity="info"
            icon={false}
            variant="outlined"
            className={classes.marginBottom_2}
          >
            {selected == null
              && (
                <>
                  {!R.isEmpty(expectation.inject_expectation_results)
                    && (
                      <>
                        <ListItem divider>
                          <ListItemText style={{ maxWidth: '200px' }} primary={<span>{t('Source')}</span>} />
                          <ListItemText primary={<span>{t('Result')}</span>} />
                          <ListItemIcon></ListItemIcon>
                        </ListItem>
                        <List
                          component="div"
                          disablePadding
                        >
                          {expectation.inject_expectation_results?.map(result => (
                            <ListItemButton key={result.sourceId} divider onClick={() => setSelected(result)}>
                              <ListItemText
                                style={{
                                  minWidth: '200px',
                                  maxWidth: '200px',
                                }}
                                primary={<span>{result.sourceName}</span>}
                              />
                              <ListItemText primary={<span>{truncate(result.result, 40)}</span>} />
                              <ListItemIcon>
                                <KeyboardArrowRightOutlined />
                              </ListItemIcon>
                            </ListItemButton>
                          ))}
                        </List>
                      </>
                    )}
                  {R.isEmpty(expectation.inject_expectation_results) && t('Pending result')}
                </>
              )}
            {selected !== null
              && (
                <pre>
                  {toJsonFormat(selected.result)}
                </pre>
              )}
          </Alert>
          <div className={classes.buttons}>
            <div>
              {selected != null
                && (
                  <Button
                    variant="contained"
                    onClick={() => setSelected(null)}
                  >
                    {t('Back')}
                  </Button>
                )}
            </div>
            <Button
              color="secondary"
              variant="contained"
              onClick={() => setOpen(false)}
            >
              {t('Close')}
            </Button>
          </div>
        </>
      </Drawer>
    </>
  );
};

export default TechnicalExpectationAsset;
