import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { useDispatch } from 'react-redux';

import { deleteInjectorContract, updateInjectorContract, updateInjectorContractMapping } from '../../../../../actions/InjectorContracts.ts';
import Button from '../../../../../components/common/button/Button';
import Drawer from '../../../../../components/common/Drawer';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store.ts';
import { attackPatternOptions } from '../../../../../utils/Option';
import { Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import InjectorContractCustomForm from './InjectorContractCustomForm';
import InjectorContractForm from './InjectorContractForm';

const InjectorContractPopover = ({ injectorContract, onUpdate, canDelete = true, canEditCustomForm = true }) => {
  const { attackPatternsMap, killChainPhasesMap } = useHelper(helper => ({
    attackPatternsMap: helper.getAttackPatternsMap(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
  }));

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

  const onSubmitInjectorContractEdit = (data) => {
    const inputValues = {
      contract_attack_patterns_ids:
          data.injector_contract_attack_patterns?.map(p => p.id),
      contract_domains: data.injector_contract_domains.map(d => d.domain_id ? d.domain_id : d),
      contract_tags_ids: data.injector_contract_tags,
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

  const onSubmitInjectorCustomFormEdit = (data, fields) => {
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
      contract_domains: data.injector_contract_domains.map((d) => {
        return {
          domain_id: d.domain_id,
          domain_name: d.domain_name,
          domain_color: d.domain_color,
        };
      }),
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
      injector_contract_tags: injectorContract.injector_contract_tags ?? [],
    };
  } else {
    initialValues = {
      injector_contract_attack_patterns: injectorContractAttackPatterns,
      injector_contract_domains: injectorContract.injector_contract_domains,
      injector_contract_tags: injectorContract.injector_contract_tags ?? [],
    };
  }

  return (
    <>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
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
        {canDelete && (
          <MenuItem
            onClick={handleOpenDelete}
            disabled={!injectorContract.injector_contract_custom}
          >
            {t('Delete')}
          </MenuItem>
        )}
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
          <Button variant="secondary" onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button variant="primary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the injector contract')}
      >
        {canEditCustomForm && injectorContract.injector_contract_custom ? (
          <InjectorContractCustomForm
            initialValues={initialValues}
            editing
            onSubmit={onSubmitInjectorCustomFormEdit}
            handleClose={handleCloseEdit}
            contractTemplate={injectorContract}
          />
        ) : (
          <InjectorContractForm
            initialValues={initialValues}
            editing
            onSubmit={onSubmitInjectorContractEdit}
            handleClose={handleCloseEdit}
          />
        )}
      </Drawer>
    </>
  );
};

export default InjectorContractPopover;
