import { type FunctionComponent } from 'react';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import DrawerBreadcrumb from '../../../common/DrawerBreadcrumb';
import type { EventFormData } from './event-types';
import EventCreationForm from './EventCreationForm';

interface Props {
  open: boolean;
  onClose: () => void;
  onBack: () => void;
  onSave: (data: EventFormData) => void;
  initialData?: EventFormData;
  isEditing?: boolean;
  defaultEventName?: string;
}

const ConfigureEventDetail: FunctionComponent<Props> = ({
  open,
  onClose,
  onBack,
  onSave,
  initialData,
  isEditing = false,
  defaultEventName,
}) => {
  const { t } = useFormatter();

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={isEditing ? t('Update Event') : t('Add Event')}
    >
      <div>
        <DrawerBreadcrumb
          parentLabel={t('Add Component')}
          currentLabel={isEditing ? t('Update Event') : t('Add Event')}
          onBack={onBack}
        />
        <EventCreationForm
          onSubmit={onSave}
          onCancel={onClose}
          initialData={initialData}
          submitLabel={isEditing ? t('Update Event') : t('Add Event')}
          defaultName={defaultEventName}
        />
      </div>
    </Drawer>
  );
};

export default ConfigureEventDetail;
