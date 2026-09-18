import { useContext, useEffect, useMemo, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { useHelper } from '../../../../../../store';
import type {CredentialOutput} from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import CredentialsList from "../../../../assets/credentials/CredentialsList";
import CredentialPopover from "../../../../assets/credentials/CredentialPopover";
import InjectAddCredentials from "../../../../simulations/simulation/injects/credentials/InjectAddCredentials";

interface Props {
  name: string;
  credentialReferences?: string[];
  disabled?: boolean;
  errorLabel?: string | null;
  label?: string | boolean;
}
const InjectCredentialReferencesList = ({ name, credentialReferences = [], disabled = false, errorLabel, label }: Props) => {
  const { control, setValue } = useFormContext();
  const { fetchCredentialReferencesByIds } = useContext(CredentialReferenceContext);
  const [credentials, setCredentials] = useState<CredentialOutput[]>([]);
  const { credentialsMap } = useHelper((helper: CredentialHelper) => ({ credentialsMap: helper.getCredentialsMap() }));

  const credentialIdsWatched = useWatch({
    control,
    name,
  }) as string[];

  const credentialIds = useMemo(() => {
    return Array.isArray(credentialIdsWatched) ? credentialIdsWatched : [];
  }, [credentialIdsWatched]);

  useEffect(() => {
    const credentials =credentialIds.map(id => credentialsMap[id]).filter(e => e !== undefined) as CredentialOutput[];
    const missingIds = credentialIds.filter(id => !credentialsMap[id]);

    if (missingIds.length > 0) {
      fetchCredentialsByIds(missingIds).then(result => setCredentials([...result.data, ...credentials]));
    } else {
      setCredentials(credentials);
    }
  }, [credentialIds]);

  const onCredentialChange = (credentialIds: string[]) => setValue(name, credentialIds, { shouldValidate: true });
  const onRemoveCredential = (credentialId: string) => setValue(name,credentialIds.filter(id => id !== credentialId), { shouldValidate: true });

  return (
    <>
      <CredentialsList
        credentials={credentials}
        withHeaders
        renderActions={credential => (
          <CredentialPopover
            credentialId={credential.credential_id!}
            credentialName={credential.credential_name!}
            onUpdate={credential => onCredentialChange([credential.credential_id!])}
            onDelete={onRemoveCredential}
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
        />
      </Can>
    </>
  );
};

export default InjectCredentialReferencesList;
