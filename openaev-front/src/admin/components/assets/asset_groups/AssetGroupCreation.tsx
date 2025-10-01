import { type FunctionComponent, useState } from 'react';

import { addAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type AssetGroup, type AssetGroupInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type UserStore } from '../../teams/players/Player';
import AssetGroupForm from './AssetGroupForm';

interface Props { onCreate: (result: AssetGroup) => void }

const AssetGroupCreation: FunctionComponent<Props> = ({ onCreate }) => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();
  const onSubmit = (data: AssetGroupInput) => {
    dispatch(addAssetGroup(data)).then(
      (result: {
        result: string;
        entities: { asset_groups: Record<string, UserStore> };
      }) => {
        if (result.result) {
          if (onCreate) {
            const created = result.entities.asset_groups[result.result];
            onCreate(created);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new asset group')}
      >
        <AssetGroupForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default AssetGroupCreation;
