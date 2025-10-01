import { DeleteOutlined } from '@mui/icons-material';
import { Card, IconButton, Typography } from '@mui/material';
import { useEffect } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import CheckboxFieldController from '../../../../components/fields/CheckboxFieldController';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type ContractOutputElement, type RegexGroup } from '../../../../utils/api-types';

interface Props {
  prefixName: string;
  index: number;
  remove: (index: number) => void;
}

const useStyles = makeStyles()(theme => ({
  outputParserElement: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr auto',
    columnGap: theme.spacing(1),
    rowGap: theme.spacing(2),
    padding: theme.spacing(2),
    alignItems: 'start',
  },
  outputValueTitle: {
    marginBottom: 0,
    marginTop: theme.spacing(2),
    gridColumn: 'span 2',
  },
}));

const ContractOutputElementCard = ({ prefixName, index, remove }: Props) => {
  const { classes } = useStyles();
  const { control, watch } = useFormContext();
  const { t } = useFormatter();

  const defaultFields = {
    credentials: ['username', 'password'],
    portscan: ['host', 'port', 'service'],
    cve: ['id', 'host', 'severity'],
  };

  const selectedContractOutputElementType = watch(`${prefixName}.${index}.contract_output_element_type`) as keyof typeof defaultFields | undefined;
  const { fields, append: regexGroupsAppend, remove: regexGroupsRemove } = useFieldArray({
    control,
    name: `${prefixName}.${index}.contract_output_element_regex_groups`,
  });
  const regexGroupsField = fields as (RegexGroup & { id: string })[];

  type ContractOutputElementType = ContractOutputElement['contract_output_element_type'];
  const contractOutputElementTypes: ContractOutputElementType[] = [
    'text', 'number', 'port', 'portscan', 'ipv4', 'ipv6', 'credentials', 'cve',
  ];

  const outputParserTypeList = contractOutputElementTypes.map(type => ({
    value: type,
    label: t(type.charAt(0).toUpperCase() + type.slice(1)),
  }));

  useEffect(() => {
    if (!selectedContractOutputElementType) return;

    const fields: string[] = defaultFields[selectedContractOutputElementType] || [selectedContractOutputElementType];
    regexGroupsRemove();
    fields.forEach((field) => {
      const existingGroup = regexGroupsField.find(group => group.regex_group_field === field);
      regexGroupsAppend({
        ...existingGroup?.regex_group_id && { regex_group_id: existingGroup?.regex_group_id },
        regex_group_field: field,
        regex_group_index_values: existingGroup?.regex_group_index_values || '',
      });
    });
  }, [selectedContractOutputElementType]);

  const getRegexIndexesValueName = (fieldName: string) => {
    const indexField = regexGroupsField.findIndex(r => r.regex_group_field === fieldName);
    return `${prefixName}.${index}.contract_output_element_regex_groups.${indexField}.regex_group_index_values`;
  };

  return (
    <Card className={classes.outputParserElement} variant="outlined">
      <TextFieldController name={`${prefixName}.${index}.contract_output_element_name` as const} label={t('Name')} required />
      <TextFieldController name={`${prefixName}.${index}.contract_output_element_key` as const} label={t('Key')} required />
      <SelectFieldController name={`${prefixName}.${index}.contract_output_element_type` as const} label={t('Type')} items={outputParserTypeList} required />
      <IconButton
        onClick={() => remove(index)}
        size="small"
        color="primary"
      >
        <DeleteOutlined />
      </IconButton>
      <TagFieldController style={{ gridColumn: 'span 4' }} name={`${prefixName}.${index}.contract_output_element_tags` as const} label={t('Tags')} />
      <CheckboxFieldController style={{ gridColumn: 'span 4' }} name={`${prefixName}.${index}.contract_output_element_is_finding` as const} label={t('create_findings')} />
      <Typography
        sx={{
          margin: 0,
          gridColumn: 'span 4',
        }}
        variant="h3"
      >
        {`${t('Regex group rules')} * :`}
      </Typography>
      <TextFieldController
        variant="outlined"
        style={{ gridColumn: 'span 4' }}
        name={`${prefixName}.${index}.contract_output_element_rule` as const}
        required
        endAdornmentLabel="/gm"
      />
      {regexGroupsField.length > 0 && (
        <Typography className={classes.outputValueTitle} variant="h3">
          {`${t('Output value')}`}
        </Typography>
      )}
      {regexGroupsField.toSorted((a, b) => a.regex_group_field.localeCompare(b.regex_group_field)).map((field, indexField) => (
        <div
          style={{
            gridColumn: 'span 4',
            display: 'grid',
            gridTemplateColumns: '1fr 5fr',
          }}
          key={field.id}
        >
          <Typography
            key={field.regex_group_field}
            sx={{
              margin: 0,
              alignSelf: 'center',
            }}
            variant="h3"
          >
            {t(field.regex_group_field.charAt(0).toUpperCase() + field.regex_group_field.slice(1))}
          </Typography>
          <TextFieldController size="small" placeholder={`$${indexField + 1}`} variant="outlined" name={getRegexIndexesValueName(field.regex_group_field)} required />
        </div>
      ))}
    </Card>
  );
};

export default ContractOutputElementCard;
