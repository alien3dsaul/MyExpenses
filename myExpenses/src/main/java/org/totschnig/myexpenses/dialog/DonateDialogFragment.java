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

package org.totschnig.myexpenses.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.licence.Package;

import java.util.Locale;

public class DonateDialogFragment extends CommitSafeDialogFragment {

  private static final String KEY_PACKAGE = "extended";
  public static final String BITCOIN_ADDRESS = "1GCUGCSfFXzSC81ogHu12KxfUn3cShekMn";

  public static DonateDialogFragment newInstance(Package aPackage) {
    DonateDialogFragment fragment = new DonateDialogFragment();
    Bundle args = new Bundle();
    args.putSerializable(KEY_PACKAGE, aPackage);
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Package aPackage = getPackage();
    DonationUriVisitor listener = new DonationUriVisitor();
    final TextView message = new TextView(getActivity());
    int padding = (int) TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
    message.setPadding(padding, padding, padding, 0);
    message.setMovementMethod(LinkMovementMethod.getInstance());
    CharSequence linefeed = Html.fromHtml("<br><br>");
    message.setText(TextUtils.concat(
        getString(R.string.donate_dialog_text),
        " ",
        Html.fromHtml("<a href=\"http://myexpenses.totschnig.org/#premium\">" + getString(R.string.learn_more) + "</a>."),
        linefeed,
        getString(R.string.thank_you)
    ));
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    return builder
        .setTitle(aPackage.getButtonLabel(getContext()))
        .setView(message)
        .setPositiveButton(R.string.donate_button_paypal, listener)
        .setNeutralButton(R.string.donate_button_bitcoin, listener)
        .create();
  }

  @NonNull
  private Package getPackage() {
    Package aPackage= (Package) getArguments().getSerializable(KEY_PACKAGE);
    if (aPackage == null) aPackage = Package.Contrib;
    return aPackage;
  }

  private class DonationUriVisitor implements OnClickListener {

    @Override
    public void onClick(DialogInterface dialog, int which) {
      Intent intent;
      Activity ctx = getActivity();
      if (which == AlertDialog.BUTTON_NEUTRAL) {
        intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("bitcoin:" + BITCOIN_ADDRESS));
        if (Utils.isIntentAvailable(ctx, intent)) {
          ctx.startActivityForResult(intent, 0);
        } else {
          ClipboardManager clipboard = (ClipboardManager)
              ctx.getSystemService(Context.CLIPBOARD_SERVICE);
          clipboard.setText(BITCOIN_ADDRESS);
          Toast.makeText(ctx,
              "My Expenses Bitcoin Donation address " + BITCOIN_ADDRESS + " copied to clipboard",
              Toast.LENGTH_LONG).show();
          if (ctx instanceof MessageDialogListener) {
            ((MessageDialogListener) ctx).onMessageDialogDismissOrCancel();
          }
        }
      } else if (which == AlertDialog.BUTTON_POSITIVE) {
        String host = BuildConfig.DEBUG ? "www.sandbox.paypal.com" : "www.paypal.com" ;
        String paypalButtonId = BuildConfig.DEBUG? "TURRUESSCUG8N" : "LBUDF8DSWJAZ8";
        String uri = String.format(Locale.US,
            "https://%s/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=%s&on0=%s&os0=%s&lc=%s",
            host, paypalButtonId, "Licence", getPackage().name(), getPaypalLocale());

        intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(uri));
        ctx.startActivityForResult(intent, 0);
      }
    }
  }

  @Override
  public void onCancel(DialogInterface dialog) {
    if (getActivity() instanceof ContribInfoDialogActivity) {
      getActivity().finish();
    }
  }

  private String getPaypalLocale() {
    return Locale.getDefault().toString();
  }
}