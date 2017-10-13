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

import android.app.Activity;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.dialog.DialogUtils;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.task.TaskExecutionFragment;

import java.io.Serializable;

/**
 * methods both needed by {@link ProtectedFragmentActivity} and now defunct
 * ProtectedFragmentActivityNoAppCompat
 *
 * @author Michael Totschnig
 */
public class ProtectionDelegate {
  public static final String PROGRESS_TAG = "PROGRESS";
  public static final String ASYNC_TAG = "ASYNC_TASK";
  Activity ctx;

  public ProtectionDelegate(Activity ctx) {
    this.ctx = ctx;
  }

  protected void handleOnPause(AlertDialog pwDialog) {
    MyApplication app = MyApplication.getInstance();
    if (app.isLocked() && pwDialog != null)
      pwDialog.dismiss();
    else {
      app.setLastPause(ctx);
    }
  }

  protected AlertDialog hanldeOnResume(AlertDialog pwDialog) {
    MyApplication app = MyApplication.getInstance();
    if (app.shouldLock(ctx)) {
      if (pwDialog == null)
        pwDialog = DialogUtils.passwordDialog(ctx, false);
      DialogUtils.showPasswordDialog(ctx, pwDialog, true, null);
    }
    return pwDialog;
  }

  public void removeAsyncTaskFragment(boolean keepProgress) {
    FragmentManager m = ((FragmentActivity) ctx).getSupportFragmentManager();
    FragmentTransaction t = m.beginTransaction();
    ProgressDialogFragment f = ((ProgressDialogFragment) m.findFragmentByTag(PROGRESS_TAG));
    if (f != null) {
      if (keepProgress) {
        f.onTaskCompleted();
      } else {
        t.remove(f);
      }
    }
    t.remove(m.findFragmentByTag(ASYNC_TAG));
    t.commitAllowingStateLoss();
    //we might want to call a new task immediately after executing the last one
    m.executePendingTransactions();
  }

  public void updateProgressDialog(Object progress) {
    FragmentManager m = ((FragmentActivity) ctx).getSupportFragmentManager();
    ProgressDialogFragment f = ((ProgressDialogFragment) m.findFragmentByTag(PROGRESS_TAG));
    if (f != null) {
      if (progress instanceof Integer) {
        f.setProgress((Integer) progress);
      } else if (progress instanceof String) {
        f.appendToMessage((String) progress);
      }
    }
  }

  public <T> void startTaskExecution(int taskId, T[] objectIds,
                                     Serializable extra, int progressMessage) {
    FragmentManager m = ((FragmentActivity) ctx).getSupportFragmentManager();
    if (m.findFragmentByTag(ASYNC_TAG) != null) {
      Toast.makeText(ctx.getBaseContext(),
          "Previous task still executing, please try again later",
          Toast.LENGTH_LONG)
          .show();
    } else {
      //noinspection AndroidLintCommitTransaction
      FragmentTransaction ft = m.beginTransaction()
          .add(TaskExecutionFragment.newInstance(
              taskId,
              objectIds, extra),
              ASYNC_TAG);
      if (progressMessage != 0) {
        ft.add(ProgressDialogFragment.newInstance(progressMessage), PROGRESS_TAG);
      }
      ft.commit();
    }
  }
}
