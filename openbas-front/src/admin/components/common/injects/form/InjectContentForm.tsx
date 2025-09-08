import { HelpOutlineOutlined, RotateLeftOutlined } from '@mui/icons-material';
import { Button, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { Article, Variable } from '../../../../../utils/api-types';
import { type ContractElement, type InjectorContractConverted } from '../../../../../utils/api-types-custom';
import { AbilityContext, Can } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import AssetGroupPopover from '../../../assets/asset_groups/AssetGroupPopover';
import AssetGroupsList from '../../../assets/asset_groups/AssetGroupsList';
import InjectAddAssetGroups from '../../../simulations/simulation/injects/asset_groups/InjectAddAssetGroups';
import AvailableVariablesDialog from '../../../simulations/simulation/variables/AvailableVariablesDialog';
import type { ExpectationInput } from '../expectations/Expectation';
import InjectExpectations from '../expectations/InjectExpectations';
import InjectArticlesList from './articles/InjectArticlesList';
import InjectChallengesList from './challenges/InjectChallengesList';
import InjectDocumentsList from './documents/InjectDocumentsList';
import InjectEndpointsList from './endpoints/InjectEndpointsList';
import InjectContentFieldComponent from './InjectContentFieldComponent';
import InjectTeamsList from './teams/InjectTeamsList';

interface Props {
  injectorContractContent: InjectorContractConverted['convertedContent'];
  injectId: string;
  isAtomic: boolean;
  readOnly?: boolean;
  articles?: Article[];
  uriVariable?: string;
  variables?: Variable[];
}

const InjectContentForm = ({
  injectorContractContent,
  injectId,
  isAtomic,
  readOnly,
  articles = [],
  uriVariable = '',
  variables = [],
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control, setValue, getValues } = useFormContext();
  const ability = useContext(AbilityContext);

  const injectorContractFields = injectorContractContent.fields;
  const fieldsMap = new Map<string, ContractElement>();
  injectorContractFields.forEach((field) => {
    fieldsMap.set(field.type, field);
  });

  // -- TEAMS --
  const renderTeams = (
    <InjectTeamsList
      readOnly={fieldsMap.get('team')?.readOnly || readOnly}
      hideEnabledUsersNumber={isAtomic}
    />
  );

  // -- ASSETS --
  const renderTargetedAssets = (
    <InjectEndpointsList
      name="inject_assets"
      disabled={fieldsMap.get('asset')?.readOnly || readOnly}
      platforms={getValues('inject_injector_contract.injector_contract_platforms')}
      architectures={getValues('inject_injector_contract.injector_contract_arch')}
    />
  );

  // -- ASSETS GROUPS --
  const injectAssetGroupIds = useWatch({
    control,
    name: 'inject_asset_groups',
  }) as string[];
  const onAssetGroupChange = (assetGroupIds: string[]) => setValue('inject_asset_groups', assetGroupIds);
  const removeAssetGroup = (assetGroupId: string) => setValue('inject_asset_groups', injectAssetGroupIds.filter(id => id !== assetGroupId));

  const renderTargetedAssetGroups = (
    <div>
      <AssetGroupsList
        assetGroupIds={injectAssetGroupIds}
        renderActions={assetGroup => (
          <AssetGroupPopover
            disabled={fieldsMap.get('asset-group')?.readOnly || readOnly}
            assetGroup={assetGroup}
            inline
            onRemoveAssetGroupFromList={removeAssetGroup}
          />
        )}
      />

      <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
        <InjectAddAssetGroups
          disabled={fieldsMap.get('asset-group')?.readOnly || readOnly}
          assetGroupIds={injectAssetGroupIds}
          onSubmit={onAssetGroupChange}
        />
      </Can>

    </div>
  );

  // -- ARTICLES --
  const renderArticles = (
    <InjectArticlesList
      allArticles={articles}
      readOnly={fieldsMap.get('article')?.readOnly || readOnly}
    />
  );

  // -- CHALLENGES --
  const renderChallenges = <InjectChallengesList readOnly={fieldsMap.get('challenge')?.readOnly || readOnly} />;

  // -- EXPECTATIONS --
  const injectExpectations = useWatch({
    control,
    name: 'inject_content.expectations',
  }) as ExpectationInput[];
  const predefinedExpectations: ExpectationInput[] = injectorContractFields
    .filter(n => n.type === 'expectation')
    .flatMap(f => f.predefinedExpectations ?? []);
  const onExpectationChange = (expectationIds: ExpectationInput[]) => setValue('inject_content.expectations', expectationIds);

  const renderExpectations = (
    <InjectExpectations
      predefinedExpectationDatas={predefinedExpectations}
      expectationDatas={injectExpectations}
      handleExpectations={onExpectationChange}
      readOnly={fieldsMap.get('expectation')?.readOnly || readOnly}
    />
  );

  // -- DOCUMENTS --
  const renderDocuments = (
    <InjectDocumentsList
      hasAttachments={fieldsMap.has('attachment')}
      readOnly={fieldsMap.get('attachment')?.readOnly || readOnly}
    />
  );

  // -- DYNAMIC FIELDS --
  const [openVariables, setOpenVariables] = useState(false);
  const openVariablesDialog = () => setOpenVariables(true);
  const dynamicFields = injectorContractFields
    .filter(n => n.type !== 'team' && n.type !== 'asset' && n.type !== 'asset-group' && n.type !== 'article' && n.type !== 'challenge' && n.type !== 'expectation' && n.type !== 'attachment')
    .map(field => ({
      ...field,
      key: `inject_content.${field.key}`,
      type: field.type as 'number' | 'text' | 'checkbox' | 'textarea' | 'tags' | 'select' | 'choice' | 'dependency-select',
    }));

  const resetDefaultValue = () => {
    dynamicFields
      .forEach((field) => {
        let defaultValue = field.cardinality === '1' ? field.defaultValue?.[0] : field.defaultValue;
        if (
          field.type === 'textarea'
          && field.richText
          && defaultValue
          && defaultValue.length > 0
        ) {
          defaultValue = (defaultValue as string ?? '').replaceAll('<#list challenges as challenge>', '&lt;#list challenges as challenge&gt;')
            .replaceAll('<#list articles as article>', '&lt;#list articles as article&gt;')
            .replaceAll('</#list>', '&lt;/#list&gt;');
        }
        setValue(field.key, defaultValue);
      });
  };

  const renderDynamicFields = (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      {
        (dynamicFields ?? []).map(field => (
          <InjectContentFieldComponent
            key={field.key}
            field={field}
            readOnly={readOnly || field.readOnly}
          />
        ))
      }
    </div>
  );

  const injectContentParts = [
    {
      title: t('Targeted teams'),
      renderRightButton: !isAtomic && (
        <SwitchFieldController
          name="inject_all_teams"
          label={<strong>{t('All teams')}</strong>}
          disabled={fieldsMap.get('teams')?.readOnly || readOnly}
          size="small"
        />
      ),
      render: renderTeams,
      show: fieldsMap.has('team'),
    },
    {
      title: t('Source assets'),
      render: renderTargetedAssets,
      show: fieldsMap.has('asset'),
    },
    {
      title: t('Source asset groups'),
      render: renderTargetedAssetGroups,
      show: fieldsMap.has('asset-group'),
    },
    {
      title: t('Media pressure to publish'),
      render: renderArticles,
      show: fieldsMap.has('article'),
    },
    {
      title: t('Challenges to publish'),
      render: renderChallenges,
      show: fieldsMap.has('challenge'),
    },
    {
      title: t('Inject data'),
      render: renderDynamicFields,
      renderLeftButton: (!isAtomic || (ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) || (injectId && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId))))
        && (
          <Tooltip title={t('Reset to default values')}>
            <IconButton
              color="primary"
              disabled={fieldsMap.get('expectation')?.readOnly || readOnly}
              onClick={resetDefaultValue}
              size="small"
            >
              <RotateLeftOutlined />
            </IconButton>
          </Tooltip>
        ),
      renderRightButton: (
        <Button
          color="primary"
          startIcon={<HelpOutlineOutlined />}
          variant="outlined"
          size="small"
          onClick={openVariablesDialog}
        >
          {t('Available variables')}
        </Button>
      ),
      show: true,
    },
    {
      title: t('Inject expectations'),
      render: renderExpectations,
      show: fieldsMap.has('expectation'),
    },
    {
      title: t('Inject documents'),
      render: renderDocuments,
      show: true,
    },
  ];

  return (
    <>
      {injectContentParts.filter(part => part.show).map(part => (
        <div
          key={part.title}
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <Typography variant="h5">
            {part.title}
          </Typography>
          {part.renderLeftButton}
          <div style={{ marginLeft: 'auto' }}>
            {part.renderRightButton}
          </div>
          <div style={{ width: '100%' }}>
            {part.render}
          </div>
        </div>
      ))}
      <AvailableVariablesDialog
        uriVariable={uriVariable}
        variables={variables}
        open={openVariables}
        handleClose={() => setOpenVariables(false)}
        variablesFromInjectorContract={injectorContractContent.variables ?? []}
      />
    </>
  );
};

export default InjectContentForm;
