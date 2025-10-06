package io.openaev.utils.base;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import lombok.Getter;

@Getter
public class ExternalServiceBase {

  private final SettingRepository settingRepository;

  public boolean serviceAvailable = false;

  public ExternalServiceBase(SettingRepository settingRepository) {
    this.settingRepository = settingRepository;
  }

  protected void saveServiceState(String key, boolean state) {
    Setting imapSetting = this.settingRepository.findByKey(key).orElse(new Setting(key, null));
    imapSetting.setValue(String.valueOf(state));
    this.settingRepository.save(imapSetting);
    this.serviceAvailable = state;
  }
}
