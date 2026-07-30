import { RestartAlt } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';

import { useFormatter } from '../../../../../components/i18n';
import type { ContractElement } from '../../../../../utils/api-types-custom';
import InjectDataFieldItem, { type FieldLink } from './InjectDataFieldItem';

interface ActionInjectDataProps {
  panelOpen?: boolean;
  loading: boolean;
  fields: ContractElement[];
  fieldValues: Record<string, unknown>;
  fieldLinks: Record<string, FieldLink>;
  autoLinkedFields?: Set<string>;
  noLinkFields?: Set<string>;
  onResetDefaults: () => void;
  onValueChange: (fieldKey: string, value: string) => void;
  onLink: (fieldKey: string, link: FieldLink) => void;
  onUnlink: (fieldKey: string) => void;
  onToggleLocalScope: (fieldKey: string, localScope: boolean) => void;
}

const ActionInjectData = ({
  panelOpen = true,
  loading,
  fields,
  fieldValues,
  fieldLinks,
  autoLinkedFields = new Set(),
  noLinkFields = new Set(),
  onResetDefaults,
  onValueChange,
  onLink,
  onUnlink,
  onToggleLocalScope,
}: ActionInjectDataProps) => {
  const { t } = useFormatter();

  if (!loading && fields.length === 0) return null;

  return (
    <Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        mb: 1,
      }}
      >
        <Typography variant="subtitle2" fontWeight={600}>{t('Inject Data')}</Typography>
        <Button size="small" startIcon={<RestartAlt />} onClick={onResetDefaults}>
          {t('Reset default value')}
        </Button>
      </Box>
      {loading && (
        <Typography variant="body2" color="text.secondary">
          {t('Loading contract fields...')}
        </Typography>
      )}
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        {fields.map((field) => {
          const fieldLabel = t(field.label) || field.key;
          const defaultVal = field.defaultValue != null ? String(field.defaultValue) : undefined;
          return (
            <InjectDataFieldItem
              key={field.key}
              panelOpen={panelOpen}
              fieldKey={field.key}
              fieldLabel={fieldLabel}
              value={String(fieldValues[field.key] ?? '')}
              defaultValue={defaultVal}
              link={fieldLinks[field.key] ?? null}
              readOnly={autoLinkedFields.has(field.key)}
              noLink={noLinkFields.has(field.key)}
              choices={
                field.choices && !Array.isArray(field.choices)
                  ? (field.choices as Record<string, string>)
                  : undefined
              }
              onValueChange={onValueChange}
              onLink={onLink}
              onUnlink={onUnlink}
              onToggleLocalScope={onToggleLocalScope}
            />
          );
        })}
      </Box>
    </Box>
  );
};

export default ActionInjectData;
