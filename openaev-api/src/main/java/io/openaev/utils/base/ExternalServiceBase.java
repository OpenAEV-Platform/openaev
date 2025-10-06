package io.openaev.utils.base;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import lombok.Getter;

@Getter
public abstract class ExternalServiceBase {

  public boolean serviceAvailable = false;

  public abstract SettingRepository getSettingRepository();

  protected void saveServiceState(String key, boolean state) {
    Setting imapSetting = this.getSettingRepository().findByKey(key).orElse(new Setting(key, null));
    imapSetting.setValue(String.valueOf(state));
    this.getSettingRepository().save(imapSetting);
    this.serviceAvailable = state;
  }
}
