/**
 * Copyright 2013 Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.totschnig.myexpenses.activity;

import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.sync.GoogleDriveBackendProvider;
import org.totschnig.myexpenses.sync.GoogleDriveBackendProviderFactory;

import icepick.Icepick;
import icepick.State;
import timber.log.Timber;

import static org.totschnig.myexpenses.sync.GenericAccountService.KEY_SYNC_PROVIDER_URL;

public class DriveSetupActivity extends ProtectedFragmentActivity implements
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

  /**
   * Request code for auto Google Play Services error resolution.
   */
  protected static final int REQUEST_CODE_RESOLUTION = 1;

  private static final int REQUEST_CODE_OPENER = 2;

  private static final int REQUEST_ACCOUNT_PICKER = 3;

  /**
   * Google API client.
   */
  private GoogleApiClient mGoogleApiClient;
  private DriveFolder driveFolder;
  @State
  String accountEmail;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Icepick.restoreInstanceState(this, savedInstanceState);

    if (savedInstanceState == null) {
      startActivityForResult(AccountPicker.newChooseAccountIntent(null,
          null, new String[]{"com.google"}, true, null, null, null, null),
          REQUEST_ACCOUNT_PICKER);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    Icepick.saveInstanceState(this, outState);
  }

  /**
   * Called when activity gets visible. A connection to Drive services need to
   * be initiated as soon as the activity is visible. Registers
   * {@code ConnectionCallbacks} and {@code OnConnectionFailedListener} on the
   * activities itself.
   */
  @Override
  protected void onResume() {
    super.onResume();
    connect();
  }

  private void connect() {
    if (accountEmail != null) {
      if (mGoogleApiClient == null) {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Drive.API)
            .addScope(Drive.SCOPE_FILE)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .setAccountName(accountEmail)
            .build();
      }
      mGoogleApiClient.connect();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (resultCode) {
      case RESULT_OK: {
        switch (requestCode) {
          case REQUEST_CODE_OPENER:
            DriveId driveId = data.getParcelableExtra(
                OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            driveFolder = driveId.asDriveFolder();
            break;
          case REQUEST_CODE_RESOLUTION:
            mGoogleApiClient.connect();
            break;
          case REQUEST_ACCOUNT_PICKER:
            accountEmail = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            connect();
            break;
          default:
            super.onActivityResult(requestCode, resultCode, data);
            break;
        }
        break;
      }
      case RESULT_CANCELED: {
        setResult(RESULT_CANCELED);
        finish();
        break;
      }
    }
  }

  /**
   * Called when activity gets invisible. Connection to Drive service needs to
   * be disconnected as soon as an activity is invisible.
   */
  @Override
  protected void onPause() {
    if (mGoogleApiClient != null) {
      mGoogleApiClient.disconnect();
    }
    super.onPause();
  }

  /**
   * Called when {@code mGoogleApiClient} is connected.
   */
  @Override
  public void onConnected(Bundle connectionHint) {
    Timber.i("GoogleApiClient connected");
    if (driveFolder == null) {
      IntentSender intentSender = Drive.DriveApi
          .newOpenFileActivityBuilder()
          .setMimeType(new String[]{DriveFolder.MIME_TYPE})
          .build(getGoogleApiClient());
      try {
        startIntentSenderForResult(
            intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
      } catch (SendIntentException e) {
        Timber.e(e, "Unable to send intent");
      }
    } else {
      driveFolder.getMetadata(getGoogleApiClient())
          .setResultCallback(result -> {
            if (result.getStatus().isSuccess()) {
              Metadata metadata = result.getMetadata();
              if (metadata.getCustomProperties().containsKey(GoogleDriveBackendProvider.ACCOUNT_METADATA_UUID_KEY)) {
                reportProblemToUser(getString(R.string.warning_synchronization_select_parent));
              } else {
                Intent intent = new Intent();
                Bundle bundle = new Bundle(1);
                String resourceId = metadata.getDriveId().getResourceId();
                if (resourceId != null) {
                  bundle.putString(KEY_SYNC_PROVIDER_URL, resourceId);
                  intent.putExtra(AccountManager.KEY_USERDATA, bundle);
                  intent.putExtra(AccountManager.KEY_ACCOUNT_NAME,
                      GoogleDriveBackendProviderFactory.LABEL + " - " + metadata.getTitle());
                  setResult(RESULT_OK, intent);
                  finish();
                } else {
                  reportProblemToUser("Problem while trying to fetch metadata");
                }
              }
            } else {
              reportProblemToUser("Problem while trying to fetch metadata");
            }
          });
    }
  }

  private void reportProblemToUser(String message) {
    showMessage(message);
    setResult(RESULT_CANCELED);
    finish();
  }

  /**
   * Called when {@code mGoogleApiClient} is disconnected.
   */
  @Override
  public void onConnectionSuspended(int cause) {
    Timber.i("GoogleApiClient connection suspended");
  }

  /**
   * Called when {@code mGoogleApiClient} is trying to connect but failed.
   * Handle {@code result.getResolution()} if there is a resolution is
   * available.
   */
  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    Timber.i("GoogleApiClient connection failed: %s", result);
    if (!result.hasResolution()) {
      // show the localized error dialog.
      Dialog errorDialog = GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0);
      errorDialog.setOnDismissListener(dialog -> finish());
      errorDialog.show();
      return;
    }
    try {
      result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
    } catch (SendIntentException e) {
      Timber.e(e, "Exception while starting resolution activity");
    }
  }

  /**
   * Shows a toast message.
   */
  public void showMessage(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  /**
   * Getter for the {@code GoogleApiClient}.
   */
  public GoogleApiClient getGoogleApiClient() {
    return mGoogleApiClient;
  }
}
