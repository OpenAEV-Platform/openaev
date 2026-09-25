import { Button, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { CopyAllOutlined } from '@mui/icons-material';
import { Alert, Dialog, DialogActions, DialogContent, List, ListItem, ListItemButton, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type ContractVariable } from '../../../../../actions/contract/contract';
import { type UserHelper } from '../../../../../actions/helper';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Variable } from '../../../../../utils/api-types';
import { copyToClipboard } from '../../../../../utils/utils';

interface VariableChildItemProps {
  hasChildren?: boolean;
  builtin?: boolean;
  variableKey: string;
  variableValue: string | undefined;
}

const VariableChildItem: FunctionComponent<VariableChildItemProps> = ({
  hasChildren = false,
  builtin = false,
  variableKey,
  variableValue,
}) => {
  const { t } = useFormatter();

  if (!variableValue) {
    return <></>;
  }

  const formattedVariableKey = `\${${variableKey}}`;

  if (hasChildren) {
    return (
      <ListItem divider={true} dense={true}>
        <ListItemText
          primary={variableKey}
          secondary={builtin ? t(variableValue) : variableValue}
        />
      </ListItem>
    );
  }
  return (
    <ListItemButton
      divider={true}
      dense={true}
      disabled={hasChildren}
      onClick={() => copyToClipboard(t, formattedVariableKey)}
    >
      <ListItemText
        primary={formattedVariableKey}
        secondary={builtin ? t(variableValue) : variableValue}
      />
      <CopyAllOutlined />
    </ListItemButton>
  );
};

const useStyles = makeStyles()(() => ({
  button: {
    textTransform: 'none',
    height: 18,
  },
  dialogPaper: {
    minHeight: '90vh',
    maxHeight: '90vh',
  },
}));

interface AvailableVariablesDialogProps {
  open: boolean;
  handleClose: () => void;
  variables: Variable[];
  variablesFromInjectorContract: ContractVariable[];
  uriVariable: string;
}

const AvailableVariablesDialog: FunctionComponent<
  AvailableVariablesDialogProps
> = ({ open, handleClose, variables, uriVariable, variablesFromInjectorContract }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const [tab, setTab] = useState('1');

  const me = useHelper((helper: UserHelper) => helper.getMe());

  return (
    <Dialog
      onClose={handleClose}
      open={open}
      fullWidth={true}
      maxWidth="md"
      PaperProps={{ elevation: 1 }}
      TransitionComponent={Transition}
      classes={{ paper: classes.dialogPaper }}
    >
      <Tabs
        value={tab}
        onValueChange={setTab}
        panels="external"
        style={{
          marginLeft: 24,
          marginTop: 24,
        }}
      >
        <TabsList>
          <TabsTrigger value="1">{t('Builtin variables')}</TabsTrigger>
          {uriVariable && <TabsTrigger value="2">{t('Custom variables')}</TabsTrigger>}
        </TabsList>
      </Tabs>
      <DialogContent>
        {tab === '1' && (
          <List>
            {variablesFromInjectorContract.map((variable) => {
              return (
                <div key={variable.key}>
                  <VariableChildItem
                    builtin
                    hasChildren={
                      variable.children && variable.children.length > 0
                    }
                    variableKey={variable.key}
                    variableValue={variable.label}
                  />
                  {variable.children && variable.children.length > 0 && (
                    <List component="div" disablePadding>
                      {variable.children.map((variableChild) => {
                        const variableChildKey = `\${${variableChild.key}}`;
                        return (
                          <ListItemButton
                            key={variableChild.key}
                            divider={true}
                            dense={true}
                            sx={{ pl: 4 }}
                            onClick={() => copyToClipboard(t, variableChildKey)}
                          >
                            <ListItemText
                              primary={variableChildKey}
                              secondary={t(variableChild.label)}
                            />
                            <CopyAllOutlined />
                          </ListItemButton>
                        );
                      })}
                    </List>
                  )}
                </div>
              );
            })}
          </List>
        )}
        {tab === '2' && uriVariable && (
          <>
            <Alert severity="info">
              {t('Please follow this link to')}
              {/* TODO: validate when migrate to new react router version */}
              {/* eslint-disable-next-line @typescript-eslint/ban-ts-comment */}
              {/* @ts-ignore */}
              <Button asChild priority="tertiary" size="sm" className={classes.button} onClick={handleClose}>
                <Link to={uriVariable}>
                  {me.user_is_planner
                    ? t('manage custom variables')
                    : t('view custom variables')}
                </Link>
              </Button>
            </Alert>
            <List>
              {variables.map(variable => (
                <div key={variable.variable_key}>
                  <VariableChildItem
                    variableKey={variable.variable_key}
                    variableValue={variable.variable_value}
                  />
                </div>
              ))}
            </List>
          </>
        )}
      </DialogContent>

      <DialogActions>
        <Button type="button" priority="secondary" onClick={handleClose}>{t('Close')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default AvailableVariablesDialog;
