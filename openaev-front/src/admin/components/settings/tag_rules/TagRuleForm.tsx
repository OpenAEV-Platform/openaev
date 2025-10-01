import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Typography } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useContext, useState } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import TagFieldSingle from '../../../../components/fields/TagFieldSingle';
import { useFormatter } from '../../../../components/i18n';
import { type TagRuleInput, type TagRuleOutput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { zodImplement } from '../../../../utils/Zod';
import AssetGroupPopover from '../../assets/asset_groups/AssetGroupPopover';
import AssetGroupsList from '../../assets/asset_groups/AssetGroupsList';
import { PermissionsContext } from '../../common/Context';
import InjectAddAssetGroups from '../../simulations/simulation/injects/asset_groups/InjectAddAssetGroups';
import OPEN_CTI_TAG_NAME from './TagRuleConstants';

interface Props {
  onSubmit: SubmitHandler<TagRuleInput>;
  editing?: boolean;
  initialValues?: TagRuleOutput;
}

const TagRuleForm: FunctionComponent<Props> = ({
  onSubmit,
  editing,
  initialValues = {
    tag_name: '',
    asset_groups: [],
  },
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [assetGroupIds] = useState<string[]>(Object.keys(initialValues.asset_groups ?? []));

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<TagRuleInput>({
    mode: 'onChange',
    resolver: zodResolver(
      zodImplement<TagRuleInput>().with({
        tag_name: z.string().min(1, { message: t('Should not be empty') }),
        asset_groups: z.string().array().optional(),
      }),
    ),
    defaultValues: {
      tag_name: initialValues.tag_name,
      asset_groups: assetGroupIds,
    },
  });

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <form id="tagForm" onSubmit={handleSubmitWithoutPropagation}>
      <Typography
        variant="h5"
        style={{
          fontWeight: 500,
          marginTop: 0,
        }}
      >
        {t('Tag')}
      </Typography>
      <Controller
        control={control}
        name="tag_name"
        render={({ field: { onChange, value } }) => {
          return (
            <TagFieldSingle
              name="tag_name"
              label={t('Select a Tag')}
              fieldValue={value}
              fieldOnChange={onChange}
              errors={errors}
              style={{ marginTop: 20 }}
              disabled={value == OPEN_CTI_TAG_NAME}
              forbiddenOptions={value !== OPEN_CTI_TAG_NAME ? [OPEN_CTI_TAG_NAME] : []}
            />
          );
        }}
      />
      <Typography
        variant="h5"
        style={{
          fontWeight: 500,
          marginTop: 50,
        }}
      >
        {t('Asset groups')}
      </Typography>
      <Controller
        name="asset_groups"
        control={control}
        render={({ field: { onChange, value } }) => {
          const assetGroupIds = value ?? [];

          return (
            <>
              <AssetGroupsList
                assetGroupIds={assetGroupIds}
                renderActions={assetGroup => (
                  <AssetGroupPopover
                    inline
                    assetGroup={assetGroup}
                    onRemoveAssetGroupFromList={result =>
                      onChange(assetGroupIds.filter(ag => ag !== result))}
                    onDelete={result => onChange(assetGroupIds.filter(ag => ag !== result))}
                    removeAssetGroupFromListMessage="Remove from the Asset Rule"
                    disabled={permissions.readOnly}
                  />
                )}
              />
              <Can I={ACTIONS.ACCESS} a={SUBJECTS.ASSETS}>
                <InjectAddAssetGroups
                  assetGroupIds={assetGroupIds}
                  onSubmit={(result) => {
                    onChange(result);
                  }}
                />
              </Can>
            </>
          );
        }}
      />

      <div style={{
        float: 'right',
        marginTop: 20,
      }}
      >
        <Button
          variant="contained"
          color="secondary"
          type="submit"
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default TagRuleForm;
