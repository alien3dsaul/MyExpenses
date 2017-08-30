/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.totschnig.myexpenses.activity;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.provider.DocumentFile;
import android.widget.Toast;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.BackupListDialogFragment;
import org.totschnig.myexpenses.dialog.BackupSourcesDialogFragment;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment;
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment.ConfirmationDialogListener;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.dialog.MessageDialogFragment;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.task.RestoreTask;
import org.totschnig.myexpenses.task.TaskExecutionFragment;
import org.totschnig.myexpenses.util.AppDirHelper;
import org.totschnig.myexpenses.util.FileUtils;
import org.totschnig.myexpenses.util.Result;
import org.totschnig.myexpenses.util.ShareUtils;
import org.totschnig.myexpenses.util.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import timber.log.Timber;

import static org.totschnig.myexpenses.task.RestoreTask.KEY_DIR_NAME_LEGACY;

public class BackupRestoreActivity extends ProtectedFragmentActivity
    implements ConfirmationDialogListener {
  public static final String FRAGMENT_TAG = "BACKUP_SOURCE";

  private boolean calledFromOnboarding = false;

  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeIdTranslucent());
    super.onCreate(savedInstanceState);
    ComponentName callingActivity = getCallingActivity();
    if (callingActivity != null && Utils.getSimpleClassNameFromComponentName(callingActivity)
        .equals(SplashActivity.class.getSimpleName())) {
      calledFromOnboarding = true;
      Timber.i("Called from onboarding");
    }
    if (savedInstanceState!=null) {
      return;
    }
    String action = getIntent().getAction();
    if (action != null && action.equals("myexpenses.intent.backup")) {
      Result appDirStatus = AppDirHelper.checkAppDir(this);
      if (!appDirStatus.success) {
        abort(appDirStatus.print(this));
        return;
      }
      MessageDialogFragment.newInstance(
          R.string.menu_backup,
          getString(R.string.warning_backup,
              FileUtils.getPath(this, AppDirHelper.getAppDir(this).getUri())),
          new MessageDialogFragment.Button(android.R.string.yes,
              R.id.BACKUP_COMMAND, null), null,
          MessageDialogFragment.Button.noButton())
          .show(getSupportFragmentManager(), "BACKUP");
    } else {
      if (getIntent().getBooleanExtra("legacy", false)) {
        Result appDirStatus = AppDirHelper.checkAppDir(this);
        if (appDirStatus.success) {
          openBrowse();
        } else {
          abort(appDirStatus.print(this));
        }
      } else {
        BackupSourcesDialogFragment.newInstance().show(
            getSupportFragmentManager(), FRAGMENT_TAG);
      }
    }
  }

  private void abort(String message) {
    Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show();
    setResult(RESULT_CANCELED);
    finish();
  }

  private void showRestoreDialog(Uri fileUri, int restorePlanStrategie) {
    Bundle bundle = buildRestoreArgs(fileUri, restorePlanStrategie);
    bundle.putInt(ConfirmationDialogFragment.KEY_TITLE, R.string.pref_restore_title);
    bundle.putString(
        ConfirmationDialogFragment.KEY_MESSAGE,
        getString(R.string.warning_restore,
            DialogUtils.getDisplayName(fileUri)));
    bundle.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
        R.id.RESTORE_COMMAND);
    ConfirmationDialogFragment.newInstance(bundle).show(getSupportFragmentManager(),
        "RESTORE");
  }

  private Bundle buildRestoreArgs(Uri fileUri, int restorePlanStrategie) {
    Bundle bundle = new Bundle();
    bundle.putInt(RestoreTask.KEY_RESTORE_PLAN_STRATEGY, restorePlanStrategie);
    bundle.putParcelable(TaskExecutionFragment.KEY_FILE_PATH, fileUri);
    return bundle;
  }

  /**
   * Legacy version for backups stored in application directory
   *
   * @param dir
   */
  private void showRestoreDialog(String dir) {
    Bundle b = new Bundle();
    b.putInt(ConfirmationDialogFragment.KEY_TITLE, R.string.pref_restore_title);
    b.putString(ConfirmationDialogFragment.KEY_MESSAGE,
        getString(R.string.warning_restore, dir));
    b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
        R.id.RESTORE_COMMAND);
    b.putString(KEY_DIR_NAME_LEGACY, dir);
    ConfirmationDialogFragment.newInstance(b).show(getSupportFragmentManager(),
        "RESTORE");
  }

  @Override
  public boolean dispatchCommand(int command, Object tag) {
    if (super.dispatchCommand(command, tag))
      return true;
    switch (command) {
      case R.id.BACKUP_COMMAND:
        if (AppDirHelper.checkAppFolderWarning(this)) {
          doBackup();
        } else {
          Bundle b = new Bundle();
          b.putInt(ConfirmationDialogFragment.KEY_TITLE,
              R.string.dialog_title_attention);
          b.putString(
              ConfirmationDialogFragment.KEY_MESSAGE,
              getString(R.string.warning_app_folder_will_be_deleted_upon_uninstall));
          b.putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE,
              R.id.BACKUP_COMMAND_DO);
          b.putString(ConfirmationDialogFragment.KEY_PREFKEY,
              PrefKey.APP_FOLDER_WARNING_SHOWN.getKey());
          ConfirmationDialogFragment.newInstance(b).show(
              getSupportFragmentManager(), "APP_FOLDER_WARNING");
        }
        return true;
    }
    return false;
  }

  protected void doBackup() {
    Result appDirStatus = AppDirHelper.checkAppDir(this);//TODO this check leads to strict mode violation, can we get rid of it ?
    if (appDirStatus.success) {
      startTaskExecution(TaskExecutionFragment.TASK_BACKUP, null, null,
          R.string.menu_backup);
    } else {
      Toast.makeText(getBaseContext(), appDirStatus.print(this),
          Toast.LENGTH_LONG).show();
      finish();
    }
  }

  @Override
  public void onPostExecute(int taskId, Object result) {
    super.onPostExecute(taskId, result);
    Result r = (Result) result;
    switch (taskId) {
      case TaskExecutionFragment.TASK_BACKUP: {
        if (!r.success) {
          Toast.makeText(getBaseContext(),
              r.print(this), Toast.LENGTH_LONG)
              .show();
        } else {
          Uri backupFileUri = ((DocumentFile) r.extra[0]).getUri();
          Toast.makeText(getBaseContext(),
              getString(r.getMessage(), FileUtils.getPath(this, backupFileUri)), Toast.LENGTH_LONG)
              .show();
          if (PrefKey.PERFORM_SHARE.getBoolean(false)) {
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(backupFileUri);
            ShareUtils.share(this, uris,
                PrefKey.SHARE_TARGET.getString("").trim(),
                "application/zip");
          }
        }
        finish();
        break;
      }
    }
  }

  @Override
  protected void onPostRestoreTask(Result result) {
    super.onPostRestoreTask(result);
    if (result.success) {
      setResult(RESULT_RESTORE_OK);
    }
    finish();
  }

  @Override
  public void onProgressUpdate(Object progress) {
    Toast.makeText(getBaseContext(), ((Result) progress).print(this),
        Toast.LENGTH_LONG).show();
  }

  public void onSourceSelected(Uri mUri, int restorePlanStrategie) {
    if (calledFromOnboarding) {
      doRestore(buildRestoreArgs(mUri, restorePlanStrategie));
    } else {
      showRestoreDialog(mUri, restorePlanStrategie);
    }
  }

  /**
   * Legacy callback from BackupListDialogFragment for backups stored in
   * application directory
   *
   * @param dirOrFile
   */
  public void onSourceSelected(String dirOrFile, int restorePlanStrategie) {
    if (dirOrFile.endsWith(".zip")) {
      showRestoreDialog(Uri.fromFile(new File(AppDirHelper.getAppDir(this).getUri().getPath(), dirOrFile)),
          restorePlanStrategie);
    } else {
      showRestoreDialog(dirOrFile);
    }
  }

  @Override
  public void onPositive(Bundle args) {
    switch (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE)) {
      case R.id.BACKUP_COMMAND_DO:
        doBackup();
        break;
      case R.id.RESTORE_COMMAND:
        doRestore(args);
        break;
    }
  }

  public void openBrowse() {
    String[] backups = listBackups();
    if (backups.length == 0) {
      Toast.makeText(getBaseContext(),
          getString(R.string.restore_no_backup_found), Toast.LENGTH_LONG)
          .show();
      finish();
    } else {
      BackupListDialogFragment.newInstance(backups).show(
          getSupportFragmentManager(), FRAGMENT_TAG);
    }
  }

  // inspired by Financisto
  public String[] listBackups() {
    DocumentFile appDir = AppDirHelper.getAppDir(this);
    if (appDir.getUri().getScheme().equals("file")) {
      String[] files = new File(appDir.getUri().getPath()).list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
          // backup-yyyMMdd-HHmmss
          return filename
              .matches("backup-\\d\\d\\d\\d\\d\\d\\d\\d-\\d\\d\\d\\d\\d\\d") ||
              filename.endsWith(".zip");
        }
      });
      if (files != null) {
        Arrays.sort(files, new Comparator<String>() {
          @Override
          public int compare(String s1, String s2) {
            return s2.compareTo(s1);
          }
        });
        return files;
      }
    }
    return new String[0];
  }

  @Override
  public void onNegative(Bundle args) {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public void onDismissOrCancel(Bundle args) {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public void onMessageDialogDismissOrCancel() {
    setResult(RESULT_CANCELED);
    finish();
  }
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case ProtectionDelegate.PERMISSIONS_REQUEST_WRITE_CALENDAR:
        if (grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, getString(R.string.calendar_permission_required), Toast.LENGTH_LONG)
                .show();
          ((DialogUtils.CalendarRestoreStrategyChangedListener)
              getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).onCalendarPermissionDenied();
        }
        return;
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
}