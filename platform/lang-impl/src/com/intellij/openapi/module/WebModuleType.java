package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class WebModuleType extends WebModuleTypeBase<ModuleBuilder> {
  @NotNull
  public static WebModuleType getInstance() {
    return (WebModuleType)ModuleTypeManager.getInstance().findByID(WEB_MODULE);
  }

  @NotNull
  @Override
  public ModuleBuilder createModuleBuilder() {
    return new WebModuleBuilder();
  }

  @NotNull
  public <T> ModuleBuilder createModuleBuilder(@NotNull WebProjectTemplate<T> webProjectTemplate) {
    return new WebModuleBuilder(webProjectTemplate);
  }

}
