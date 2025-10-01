import { DeleteOutlined, ExpandMore } from '@mui/icons-material';
import {
  Accordion,
  AccordionActions,
  AccordionDetails,
  AccordionSummary,
  Badge,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { CogOutline, InformationOutline } from 'mdi-material-ui';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, type FieldArrayWithId, useFieldArray, type UseFieldArrayRemove, type UseFormReturn } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import { useFormatter } from '../../../../../components/i18n';
import InjectContractComponent from '../../../../../components/InjectContractComponent';
import RegexComponent from '../../../../../components/RegexComponent';
import { type ImportMapperAddInput } from '../../../../../utils/api-types';
import { type ContractElement, type InjectorContractConverted } from '../../../../../utils/api-types-custom';

const useStyles = makeStyles()(() => ({
  rulesArray: {
    gap: '10px',
    width: '100%',
    display: 'inline-grid',
    marginTop: '10px',
    alignItems: 'center',
    gridTemplateColumns: ' 1fr 3fr 50px',
  },
  container: {
    display: 'inline-flex',
    alignItems: 'center',
  },
  redStar: {
    color: 'rgb(244, 67, 54)',
    marginLeft: '2px',
  },
  red: { borderColor: 'rgb(244, 67, 54)' },
}));

interface Props {
  field: FieldArrayWithId<ImportMapperAddInput, 'import_mapper_inject_importers', 'id'>;
  methods: UseFormReturn<ImportMapperAddInput>;
  index: number;
  remove: UseFieldArrayRemove;
}

const RulesContractContent: FunctionComponent<Props> = ({
  field,
  methods,
  index,
  remove,
}) => {
  const { t, tPick } = useFormatter();
  const { classes, cx } = useStyles();

  // Fetching data

  const { control, formState: { errors } } = methods;

  const { fields: rulesFields, remove: rulesRemove, append: rulesAppend } = useFieldArray({
    control,
    name: `import_mapper_inject_importers.${index}.inject_importer_rule_attributes`,
  });

  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [injectorContractLabel, setInjectorContractLabel] = useState<string | undefined>(undefined);

  const isMandatoryField = (fieldKey: string) => {
    return ['title'].includes(fieldKey) || contractFields.find(f => f.key === fieldKey)?.mandatory;
  };

  const addRules = (contractFieldKeys: string[]) => {
    rulesAppend({
      rule_attribute_name: 'title',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });
    rulesAppend({
      rule_attribute_name: 'description',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });
    rulesAppend({
      rule_attribute_name: 'trigger_time',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });

    for (let i = 0; i < contractFieldKeys?.length; i++) {
      rulesAppend({
        rule_attribute_name: contractFieldKeys[i],
        rule_attribute_columns: '',
        rule_attribute_default_value: '',
      });
    }
    rulesAppend({
      rule_attribute_name: 'expectation_name',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });
    rulesAppend({
      rule_attribute_name: 'expectation_description',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });
    rulesAppend({
      rule_attribute_name: 'expectation_score',
      rule_attribute_columns: '',
      rule_attribute_default_value: '',
    });
  };

  useEffect(() => {
    if (methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_injector_contract`)) {
      directFetchInjectorContract(methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_injector_contract`)).then((result: { data: InjectorContractConverted }) => {
        const injectorContract = result.data;
        setInjectorContractLabel(tPick(injectorContract.injector_contract_labels));
        const tmp = injectorContract?.convertedContent?.fields
          .filter(f => !['checkbox', 'attachment', 'expectation'].includes(f.type));
        setContractFields(tmp);
      });
    }
  }, []);

  const onChangeInjectorContractId = () => {
    directFetchInjectorContract(methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_injector_contract`)).then((result: { data: InjectorContractConverted }) => {
      const injectorContract = result.data;
      setInjectorContractLabel(tPick(injectorContract.injector_contract_labels));
      const tmp = injectorContract?.convertedContent?.fields
        .filter(f => !['checkbox', 'attachment', 'expectation'].includes(f.type));
      setContractFields(tmp);
      const contractFieldKeys = tmp.map(f => f.key);
      rulesRemove();
      addRules(contractFieldKeys);
    });
  };

  const [currentRuleIndex, setCurrentRuleIndex] = useState<number | null>(null);
  const handleDefaultValueOpen = (rulesIndex: number) => {
    setCurrentRuleIndex(rulesIndex);
  };
  const handleDefaultValueClose = () => {
    setCurrentRuleIndex(null);
  };

  const [openAlertDelete, setOpenAlertDelete] = useState(false);

  const handleClickOpenAlertDelete = () => {
    setOpenAlertDelete(true);
  };

  const handleCloseAlertDelete = () => {
    setOpenAlertDelete(false);
  };

  return (
    <>
      <Accordion
        key={field.id}
        variant="outlined"
        style={{
          width: '100%',
          marginBottom: '10px',
        }}
        className={cx({ [classes.red]: !!errors.import_mapper_inject_importers?.[index] })}
      >
        <AccordionSummary
          expandIcon={<ExpandMore />}
        >
          <div className={classes.container}>
            <Typography>
              #
              {index + 1}
              {' '}
              {injectorContractLabel ?? t('New representation')}
            </Typography>
            <Tooltip title={t('Delete')}>
              <IconButton color="error" onClick={handleClickOpenAlertDelete}>
                <DeleteOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          </div>
        </AccordionSummary>
        <AccordionDetails>
          <div style={{
            display: 'flex',
            alignItems: 'end',
            gap: '8px',
          }}
          >
            <TextField
              variant="standard"
              fullWidth
              label={t('Matching type in the xls')}
              style={{ marginTop: 10 }}
              inputProps={methods.register(`import_mapper_inject_importers.${index}.inject_importer_type_value` as const)}
              InputLabelProps={{ required: true }}
              error={!!methods.formState.errors.import_mapper_inject_importers?.[index]?.inject_importer_type_value}
              helperText={methods.formState.errors.import_mapper_inject_importers?.[index]?.inject_importer_type_value?.message}
            />
            <Tooltip
              title={t(
                'This word will match in the specified column to determine the inject',
              )}
            >
              <InformationOutline
                fontSize="medium"
                color="primary"
                style={{ cursor: 'default' }}
              />
            </Tooltip>
          </div>

          <Controller
            control={control}
            name={`import_mapper_inject_importers.${index}.inject_importer_injector_contract`}
            render={({ field: { onChange, value }, fieldState: { error } }) => (
              <InjectContractComponent
                label={t('Inject type')}
                onChange={(data) => {
                  onChange(data);
                  onChangeInjectorContractId();
                }}
                error={error}
                fieldValue={value}
              />
            )}
          />
          {rulesFields.map((ruleField, rulesIndex) => {
            let cogIcon;
            if (ruleField.rule_attribute_name === 'trigger_time') {
              cogIcon = (
                <Badge
                  color="secondary"
                  variant="dot"
                  invisible={(!methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`) || methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`)?.length === 0)
                    && (!methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_additional_config.timePattern`)
                      || methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_additional_config`)?.timePattern?.length === 0)}
                >
                  <CogOutline />
                </Badge>
              );
            } else if (ruleField.rule_attribute_name === 'teams') {
              cogIcon = (
                <Badge
                  color="secondary"
                  variant="dot"
                  invisible={(!methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`) || methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`)?.length === 0)
                    && (!methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_additional_config.allTeamsValue`)
                      || methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_additional_config`)?.allTeamsValue?.length === 0)}
                >
                  <CogOutline />
                </Badge>
              );
            } else {
              cogIcon = (
                <Badge
                  color="secondary"
                  variant="dot"
                  invisible={!methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`)
                    || methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`)?.length === 0}
                >
                  <CogOutline />
                </Badge>
              );
            }
            return (
              <div key={ruleField.id} style={{ marginTop: 20 }}>
                <div className={classes.rulesArray}>
                  <Typography
                    style={{ textTransform: 'capitalize' }}
                    variant="body1"
                    {...methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_name` as const)}
                  >
                    {t(ruleField.rule_attribute_name[0].toUpperCase() + ruleField.rule_attribute_name.slice(1))}
                    {isMandatoryField(ruleField.rule_attribute_name)
                      && <span className={classes.redStar}>*</span>}
                  </Typography>
                  <Controller
                    control={control}
                    name={`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_columns` as const}
                    render={({ field: { onChange, value }, fieldState: { error } }) => (
                      <RegexComponent
                        label={t('Rule attributes columns')}
                        onChange={onChange}
                        error={error}
                        fieldValue={value}
                      />
                    )}
                  />
                  <IconButton
                    color="primary"
                    onClick={() => handleDefaultValueOpen(rulesIndex)}
                  >
                    {cogIcon}
                  </IconButton>
                </div>
                {currentRuleIndex !== null
                  && (
                    <Dialog
                      open
                      PaperProps={{ elevation: 1 }}
                      BackdropProps={{ style: { backgroundColor: 'transparent' } }}
                      onClose={handleDefaultValueClose}
                    >
                      <DialogTitle>
                        {t('Attribute mapping configuration')}
                      </DialogTitle>
                      <DialogContent>
                        <TextField
                          fullWidth
                          label={t('Default value')}
                          inputProps={methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_default_value`)}
                        />
                        {currentRuleIndex === rulesFields.findIndex(r => r.rule_attribute_name === 'trigger_time')
                          && (
                            <div style={{
                              display: 'flex',
                              alignItems: 'end',
                              gap: '8px',
                            }}
                            >
                              <TextField
                                label={t('Time pattern')}
                                fullWidth
                                style={{ marginTop: 10 }}
                                inputProps={methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_additional_config.timePattern`)}
                              />
                              <Tooltip
                                title={t(
                                  'By default we accept iso date (YYYY-MM-DD hh:mm:ss[.mmm]TZD), but you can specify your own date format in ISO notation (for instance DD.MM.YYYY hh\'h\'mm)',
                                )}
                              >
                                <InformationOutline
                                  fontSize="medium"
                                  color="primary"
                                  style={{ cursor: 'default' }}
                                />
                              </Tooltip>
                            </div>
                          )}
                        {currentRuleIndex === rulesFields.findIndex(r => r.rule_attribute_name === 'teams')
                          && (
                            <div style={{
                              display: 'flex',
                              alignItems: 'end',
                              gap: '8px',
                            }}
                            >
                              <TextField
                                label={t('All teams value')}
                                fullWidth
                                style={{ marginTop: 10 }}
                                inputProps={methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_additional_config.allTeamsValue`)}
                              />
                              <Tooltip
                                title={t(
                                  'Value that signifies all teams are targeted. A regex can be used.',
                                )}
                              >
                                <InformationOutline
                                  fontSize="medium"
                                  color="primary"
                                  style={{ cursor: 'default' }}
                                />
                              </Tooltip>
                            </div>
                          )}
                      </DialogContent>
                      <DialogActions>
                        <Button onClick={handleDefaultValueClose} autoFocus>
                          {t('Close')}
                        </Button>
                      </DialogActions>
                    </Dialog>
                  )}
              </div>
            );
          })}

        </AccordionDetails>
        <AccordionActions sx={{ padding: '16px' }}>
          <Button color="error" variant="contained" onClick={handleClickOpenAlertDelete}>{t('Delete')}</Button>
        </AccordionActions>
      </Accordion>
      <Dialog
        open={openAlertDelete}
        onClose={handleCloseAlertDelete}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this representation?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseAlertDelete}>{t('Cancel')}</Button>
          <Button
            color="secondary"
            onClick={() => {
              remove(index);
              handleCloseAlertDelete();
            }}
          >
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default RulesContractContent;
