import { FunctionComponent, useState } from "react";
import { Button, CircularProgress, Theme, Typography } from "@mui/material";
import { FiligranLoader } from "filigran-icon";
import Dialog from "../../../../components/common/dialog/Dialog";
import { PlatformSettings } from "../../../../utils/api-types";
import { Translate } from "../../../../components/i18n";

export interface Props {
  open: boolean;
  onSubmit: (value: boolean) => void;
  onClose: () => void;
  theme: Theme;
  submitFormByRemediationTab: () => Promise<boolean>
  settings: PlatformSettings;
  t: Translate;
}

const PayloadSubmitFormDialog: FunctionComponent<Props> = ({
                                                             open,
                                                             onSubmit,
                                                             onClose,
                                                             theme,
                                                             submitFormByRemediationTab,
                                                             settings,
                                                             t
                                                           }) => {


  const hasFiligranLoader = theme && !(settings?.platform_license?.license_is_validated && settings?.platform_whitemark);
  const [loading, setLoading] = useState(false);

  const handleOnClose = () => {
    onClose();
  };

  const handleOnSubmit = async () => {
    setLoading(true);

    //Check if form was valid and saved
    let isValid = submitFormByRemediationTab();

    isValid.then((isValid) => {
      onSubmit(isValid);
    });
  };

  return (
    <Dialog
      title={t('Save payload before launching?')}
      open={open}
      handleClose={handleOnClose}
      actions={(
        <>
          <Button onClick={handleOnClose}
                  disabled={loading}>
            {t('Cancel')}
          </Button>

          <Button color="secondary"
                  onClick={handleOnSubmit}
                  disabled={loading}>
            {t('Continue')}
          </Button>
        </>
      )}
    >
      <>
        <div style={{display: 'flex', justifyContent: 'space-between'}}>
          <div>
            <Typography>
              {t('You must save the current payload before launching this feature.')}
            </Typography>
            <Typography>
              {t('Would you like to save now and continue?')}
            </Typography>
          </div>

          <div style={{
            display: loading ? "flex" : "none",
            marginTop: '10px'
          }}>
            {!hasFiligranLoader ? (
              <FiligranLoader height={24} color={theme?.palette?.grey.A100}></FiligranLoader>
            ) : (
              <CircularProgress
                size={24}
                thickness={1}
              />
            )}
          </div>
        </div>
      </>
    </Dialog>
  )
};

export default PayloadSubmitFormDialog;