import { normalize } from 'normalizr';
import { useEffect, useMemo, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { findCredentialsByIds } from '../../../../../../actions/assets/credential-actions';
import { type CredentialHelper } from '../../../../../../actions/assets/credential-helper';
import { arrayOfCredentials } from '../../../../../../actions/Schema';
import * as Constants from '../../../../../../constants/ActionTypes';
import { useHelper } from '../../../../../../store';
import { type CredentialOutput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { Can } from '../../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import CredentialPopover from '../../../../assets/credentials/CredentialPopover';
import CredentialsList from '../../../../assets/credentials/CredentialsList';
import InjectAddCredentials from '../../../../simulations/simulation/injects/credentials/InjectAddCredentials';

interface Props {
  disabled?: boolean;
  errorLabel?: string | null;
  label?: string | boolean;
  multiple?: boolean;
}

const InjectCredentialReferencesList = ({ disabled = false, errorLabel, label, multiple = true }: Props) => {
  const { control, setValue } = useFormContext();
  const dispatch = useAppDispatch();
  const [credentials, setCredentials] = useState<CredentialOutput[]>([]);
  const { credentialsMap } = useHelper((helper: CredentialHelper) => ({ credentialsMap: helper.getCredentialsMap() }));

  const credentialIdsWatched = useWatch({
    control,
    name: 'inject_secret_references',
  }) as string[];

  const credentialIds = useMemo(
    () => (Array.isArray(credentialIdsWatched) ? credentialIdsWatched : []),
    [credentialIdsWatched],
  );

  // Credentials already normalized in the store are reused as-is; only the missing ones are
  // resolved server-side, then pushed to the store so sibling components benefit from them too.
  useEffect(() => {
    const knownCredentials = credentialIds
      .map(id => credentialsMap[id])
      .filter(credential => credential !== undefined);
    const missingIds = credentialIds.filter(id => !credentialsMap[id]);

    if (missingIds.length === 0) {
      setCredentials(knownCredentials);
      return;
    }
    findCredentialsByIds(missingIds).then((result: { data: CredentialOutput[] }) => {
      dispatch({
        type: Constants.DATA_FETCH_SUCCESS,
        payload: normalize(result.data, arrayOfCredentials),
      });
      setCredentials([...result.data, ...knownCredentials]);
    });
  }, [credentialIds, credentialsMap, dispatch]);

  const onCredentialChange = (updatedCredentialIds: string[]) => setValue('inject_secret_references', updatedCredentialIds, { shouldValidate: true });
  const onCredentialRemoveFromInject = (credentialId: string) => onCredentialChange(credentialIds.filter(id => id !== credentialId));

  return (
    <>
      <CredentialsList
        credentials={credentials}
        withHeaders
        renderActions={credential => (
          <CredentialPopover
            credentialId={credential.credential_id!}
            credentialName={credential.credential_name!}
            onRemove={onCredentialRemoveFromInject}
            disabled={disabled}
          />
        )}
      />
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.CREDENTIALS}>
        <InjectAddCredentials
          credentialIds={credentialIds}
          onSubmit={onCredentialChange}
          disabled={disabled}
          errorLabel={errorLabel}
          label={label}
          multiple={multiple}
        />
      </Can>
    </>
  );
};

export default InjectCredentialReferencesList;
