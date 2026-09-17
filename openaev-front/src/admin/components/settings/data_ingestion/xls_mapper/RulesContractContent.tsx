import { Badge, Button, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { DeleteOutlined, ExpandMore } from '@mui/icons-material';
import { Accordion, AccordionActions, AccordionDetails, AccordionSummary, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Typography } from '@mui/material';
import { CogOutline, InformationOutline } from 'mdi-material-ui';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, type FieldArrayWithId, useFieldArray, type UseFieldArrayRemove, type UseFormReturn } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import TextFieldFds from '../../../../../components/fields/TextFieldFds';
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
            <Tooltip>
              <TooltipTrigger asChild>
                <IconButton
                  icon={<DeleteOutlined fontSize="small" />}
                  aria-label={t('Delete')}
                  onClick={handleClickOpenAlertDelete}
                  variant="destructive"
                  priority="tertiary"
                  size="md"
                />
              </TooltipTrigger>
              <TooltipContent>{t('Delete')}</TooltipContent>
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
            <TextFieldFds
              required
              label={t('Matching type in the xls')}
              style={{ marginTop: 10 }}
              {...methods.register(`import_mapper_inject_importers.${index}.inject_importer_type_value` as const)}
              error={!!methods.formState.errors.import_mapper_inject_importers?.[index]?.inject_importer_type_value}
              helperText={methods.formState.errors.import_mapper_inject_importers?.[index]?.inject_importer_type_value?.message}
            />
            <Tooltip>
              <TooltipTrigger asChild>
                <InformationOutline
                  fontSize="medium"
                  color="primary"
                  style={{ cursor: 'default' }}
                />
              </TooltipTrigger>
              <TooltipContent>
                {t(
                  'This word will match in the specified column to determine the inject',
                )}
              </TooltipContent>
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
            // The dot on the cog says that this rule carries an advanced setting (a default value, and for some rules an extra option).
            const defaultValue = methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_default_value`);
            const additionalConfig = methods.getValues(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${rulesIndex}.rule_attribute_additional_config`);
            const hasDefaultValue = !!defaultValue && defaultValue.length > 0;
            let hasAdvancedSetting = hasDefaultValue;
            if (ruleField.rule_attribute_name === 'trigger_time') {
              hasAdvancedSetting = hasDefaultValue || !!additionalConfig?.timePattern?.length;
            } else if (ruleField.rule_attribute_name === 'teams') {
              hasAdvancedSetting = hasDefaultValue || !!additionalConfig?.allTeamsValue?.length;
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
                  <Badge invisible={!hasAdvancedSetting} accessibleText={t('Default value set')} bareAnchor="md">
                    <IconButton
                      icon={<CogOutline />}
                      aria-label={t('Default value')}
                      onClick={() => handleDefaultValueOpen(rulesIndex)}
                      priority="tertiary"
                      size="md"
                    />
                  </Badge>
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
                        <TextFieldFds
                          label={t('Default value')}
                          {...methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_default_value`)}
                        />
                        {currentRuleIndex === rulesFields.findIndex(r => r.rule_attribute_name === 'trigger_time')
                          && (
                            <div style={{
                              display: 'flex',
                              alignItems: 'end',
                              gap: '8px',
                            }}
                            >
                              <TextFieldFds
                                label={t('Time pattern')}
                                style={{ marginTop: 10 }}
                                {...methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_additional_config.timePattern`)}
                              />
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <InformationOutline
                                    fontSize="medium"
                                    color="primary"
                                    style={{ cursor: 'default' }}
                                  />
                                </TooltipTrigger>
                                <TooltipContent>
                                  {t(
                                    'By default we accept iso date (YYYY-MM-DD hh:mm:ss[.mmm]TZD), but you can specify your own date format in ISO notation (for instance DD.MM.YYYY hh\'h\'mm)',
                                  )}
                                </TooltipContent>
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
                              <TextFieldFds
                                label={t('All teams value')}
                                style={{ marginTop: 10 }}
                                {...methods.register(`import_mapper_inject_importers.${index}.inject_importer_rule_attributes.${currentRuleIndex}.rule_attribute_additional_config.allTeamsValue`)}
                              />
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <InformationOutline
                                    fontSize="medium"
                                    color="primary"
                                    style={{ cursor: 'default' }}
                                  />
                                </TooltipTrigger>
                                <TooltipContent>
                                  {t(
                                    'Value that signifies all teams are targeted. A regex can be used.',
                                  )}
                                </TooltipContent>
                              </Tooltip>
                            </div>
                          )}
                      </DialogContent>
                      <DialogActions>
                        <Button type="button" priority="secondary" onClick={handleDefaultValueClose} autoFocus>
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
          <Button type="button" variant="destructive" onClick={handleClickOpenAlertDelete}>{t('Delete')}</Button>
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
          <Button type="button" priority="secondary" onClick={handleCloseAlertDelete}>{t('Cancel')}</Button>
          <Button
            type="button"
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
