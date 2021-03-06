/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.project;

import com.intellij.openapi.util.Computable;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown on accessing indices when they're not ready, in so-called dumb mode. Possible fixes:
 * <ul>
 * <li> If {@link com.intellij.openapi.actionSystem.AnAction#actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent)} is in stack trace,
 * consider making the action not implement {@link DumbAware}.
 *
 * <li> A {@link DumbAware} action, having got this exception, may just notify the user that the requested activity is not possible while
 * indexing is in progress. It can be done via a dialog (see {@link com.intellij.openapi.ui.Messages}) or a status bar balloon
 * (see {@link DumbService#showDumbModeNotification(String)}, {@link com.intellij.openapi.actionSystem.ex.ActionUtil#showDumbModeWarning(com.intellij.openapi.actionSystem.AnActionEvent...)}).
 *
 * <li> If index access is performed from some non-urgent invokeLater activity, consider replacing it with
 * {@link DumbService#smartInvokeLater(Runnable)}. Note that this 'later' can be very late, several minutes may pass. So if that code
 * involves user interaction, {@link DumbService#smartInvokeLater(Runnable)} should probably not be used to avoid dialogs popping out of the blue.
 *
 * <li> If it's a non-urgent background process (e.g. compilation, usage search), consider replacing topmost read-action with
 * {@link DumbService#runReadActionInSmartMode(Computable)}.
 *
 * <li> If the exception comes from within Java's findClass call, and the IDE is currently performing a user-initiated action or a
 * task when skipping findClass would lead to very negative consequences (e.g. not stopping at a breakpoint), then it might be possible
 * to avoid index query by using alternative resolve (and findClass) strategy, which is significantly slower and might return null. To do this,
 * use {@link DumbService#setAlternativeResolveEnabled(boolean)}.
 *
 * <li> It's preferable to avoid the exception entirely by adding {@link DumbService#isDumb()} checks where necessary.
 * </ul>
 *
 * @author peter
 * @see DumbService
 * @see DumbAware
 */
public class IndexNotReadyException extends RuntimeException {
  private final Throwable myStartTrace;

  public IndexNotReadyException() {
    this(null);
  }

  public IndexNotReadyException(@Nullable Throwable startTrace) {
    myStartTrace = startTrace;
  }

  @Override
  public String getMessage() {
    String msg = "Please change caller according to " + IndexNotReadyException.class.getName() + " documentation";
    if (myStartTrace != null) {
      msg += "\nIndexing started at: " + ExceptionUtil.getThrowableText(myStartTrace) + "\n-----------------------------\n";
    }
    return msg;
  }
}
