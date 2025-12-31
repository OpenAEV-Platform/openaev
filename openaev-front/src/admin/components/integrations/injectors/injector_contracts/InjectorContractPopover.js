import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';

import { deleteInjectorContract, updateInjectorContract, updateInjectorContractMapping } from '../../../../../actions/InjectorContracts';
import Drawer from '../../../../../components/common/Drawer';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import { attackPatternOptions } from '../../../../../utils/Option';
import { Can } from '../../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types.js';
import InjectorContractCustomForm from './InjectorContractCustomForm';
import InjectorContractForm from './InjectorContractForm';

const InjectorContractPopover = ({ injectorContract, killChainPhasesMap, attackPatternsMap, onUpdate, isPayloadInjector }) => {
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const handlePopoverOpen = (event) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);
  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEditMapping = (data) => {
    const {
      injector_contract_attack_patterns,
      injector_contract_domains,
      ...restData
    } = data;

    const inputValues = {
      ...restData,
      contract_attack_patterns_ids:
        injector_contract_attack_patterns?.map(p => p.id),
      contract_domains: injector_contract_domains?.map(p => p.domain_id),
    };

    return dispatch(
      updateInjectorContractMapping(
        injectorContract.injector_contract_id,
        inputValues,
      ),
    ).then((result) => {
      if (result.entities && onUpdate) {
        onUpdate(result.entities.injector_contracts[result.result]);
      }
      handleCloseEdit();
    });
  };

  const onSubmitEdit = (data, fields) => {
    const injectorContractContent = JSON.parse(injectorContract.injector_contract_content);
    const newInjectorContractContent = {
      ...injectorContractContent,
      label: { en: data.injector_contract_name },
      fields: injectorContractContent.fields.map((field) => {
        const newField = { ...field };
        if (!R.isNil(fields[field.key]?.readOnly)) {
          newField.readOnly = fields[field.key]?.readOnly;
        }
        if (!R.isNil(fields[field.key]?.defaultValue)) {
          newField.defaultValue = field.cardinality === '1' ? fields[field.key]?.defaultValue : [fields[field.key]?.defaultValue];
        }
        return newField;
      }),
    };

    const inputValues = {
      contract_labels: { en: data.injector_contract_name },
      contract_attack_patterns_ids: R.pluck('id', data.injector_contract_attack_patterns),
      contract_content: JSON.stringify(newInjectorContractContent),
      contract_domains: data.injector_contract_domains, // doit être un tableau d'objets Domain complets
    };

    return dispatch(updateInjectorContract(injectorContract.injector_contract_id, inputValues))
      .then((result) => {
        if (result.entities && onUpdate) {
          onUpdate(result.entities.injector_contracts[result.result]);
        }
        handleCloseEdit();
      });
  };
  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    dispatch(deleteInjectorContract(injectorContract.injector_contract_id));
    handleCloseDelete();
  };
  const injectorContractAttackPatterns = attackPatternOptions(injectorContract.injector_contract_attack_patterns, attackPatternsMap, killChainPhasesMap);

  let initialValues;

  if (injectorContract.injector_contract_custom) {
    initialValues = {
      ...injectorContract,
      injector_contract_name: injectorContract.injector_contract_labels.en,
      injector_contract_attack_patterns: injectorContractAttackPatterns,
      injector_contract_domains: injectorContract.injector_contract_domains,
    };
  } else {
    initialValues = {
      injector_contract_attack_patterns: injectorContractAttackPatterns,
      injector_contract_domains: injectorContract.injector_contract_domains,
    };
  }

  return (
    <>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
        <IconButton color="primary" onClick={handlePopoverOpen} aria-haspopup="true" size="large">
          <MoreVert />
        </IconButton>
      </Can>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <MenuItem onClick={handleOpenEdit}>{t('Update')}</MenuItem>
        <MenuItem onClick={handleOpenDelete} disabled={!injectorContract.injector_contract_custom}>{t('Delete')}</MenuItem>
      </Menu>
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this inject contract?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the injector contract')}
      >
        {injectorContract.injector_contract_custom ? (
          <InjectorContractCustomForm
            initialValues={initialValues}
            editing={true}
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            contractTemplate={injectorContract}
            isPayloadInjector={isPayloadInjector}
          />
        ) : (
          <InjectorContractForm
            initialValues={initialValues}
            editing={true}
            onSubmit={onSubmitEditMapping}
            handleClose={handleCloseEdit}
            isPayloadInjector={isPayloadInjector}
          />
        )}
      </Drawer>
    </>
  );
};

export default InjectorContractPopover;
