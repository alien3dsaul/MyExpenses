package org.totschnig.myexpenses.util.tracking;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.totschnig.myexpenses.util.DistribHelper;
import org.totschnig.myexpenses.util.Preconditions;

public class PlatformTracker implements Tracker {
  private FirebaseAnalytics firebaseAnalytics;
  @Override
  public void init(Context context) {
    firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    firebaseAnalytics.setUserProperty("Distribution", DistribHelper.getDistribution().name());
  }

  @Override
  public void logEvent(String eventName, Bundle params) {
    Preconditions.checkNotNull(firebaseAnalytics);
    firebaseAnalytics.logEvent(eventName, params);
  }

  @Override
  public void setEnabled(boolean enabled) {
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
  }
}
