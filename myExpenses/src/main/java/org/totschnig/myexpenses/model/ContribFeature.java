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

package org.totschnig.myexpenses.model;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.text.Html;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.util.licence.LicenceHandler;
import org.totschnig.myexpenses.util.Utils;

import java.util.Date;
import java.util.Locale;

import static org.totschnig.myexpenses.util.licence.LicenceHandler.LicenceStatus.*;

//TODO separate enum definition from handler
//TODO use separate preferences object injected via DI
public enum ContribFeature {
  ACCOUNTS_UNLIMITED(false) {
    @Override
    public String buildUsageLimitString(Context context) {
      String currentLicence = getCurrentLicence(context);
      return context.getString(R.string.dialog_contrib_usage_limit_accounts, FREE_ACCOUNTS, currentLicence);
    }
  },
  PLANS_UNLIMITED(false){
    @Override
    public String buildUsageLimitString(Context context) {
      String currentLicence = getCurrentLicence(context);
      return context.getString(R.string.dialog_contrib_usage_limit_plans, FREE_PLANS, currentLicence);
    }
  },
  SECURITY_QUESTION,
  SPLIT_TRANSACTION,
  DISTRIBUTION,
  TEMPLATE_WIDGET,
  PRINT,
  ATTACH_PICTURE,
  AD_FREE(false),
  CSV_IMPORT(true, LicenceHandler.EXTENDED),
  AUTO_BACKUP(true, LicenceHandler.EXTENDED) {
    @Override
    public String buildUsagesLefString(Context ctx) {
      int usagesLeft = usagesLeft();
      return usagesLeft > 0 ? ctx.getString(R.string.warning_auto_backup_limited_trial, usagesLeft) :
          ctx.getString(R.string.warning_auto_backup_limit_reached);
    }
  },
  SYNCHRONIZATION(true, LicenceHandler.EXTENDED) {
    private String PREF_KEY = "FEATURE_SYNCHRONIZATION_FIRST_USAGE";
    private int TRIAL_DURATION_DAYS = 10;
    private long TRIAL_DURATION_MILLIS = (TRIAL_DURATION_DAYS * 24 * 60) * 60 * 1000;

    @Override
    public int recordUsage() {
      if (!hasAccess()) {
        long now = System.currentTimeMillis();
        if (getStartOfTrial(0L) == 0L) {
          MyApplication.getInstance().getSettings().edit().putLong(PREF_KEY, now).apply();
        }
        if (getEndOfTrial(now) < now) {
          return 0;
        }
      }
      return 1;
    }

    @Override
    public int usagesLeft() {
      long now = System.currentTimeMillis();
      return getEndOfTrial(now) < now ? 0 : 1;
    }

    private long getStartOfTrial(long defaultValue) {
      return MyApplication.getInstance().getSettings().getLong(PREF_KEY, defaultValue);
    }

    private long getEndOfTrial(long now) {
      return getStartOfTrial(now) + TRIAL_DURATION_MILLIS;
    }

    @Override
    public String buildUsagesLefString(Context ctx) {
      long now = System.currentTimeMillis();
      long endOfTrial = getEndOfTrial(now);
      if (endOfTrial < now) {
        return ctx.getString(R.string.warning_synchronization_limit_reached);
      } else {
        return ctx.getString(R.string.warning_synchronization_limited_trial,
            Utils.getDateFormatSafe(ctx).format(new Date(endOfTrial)));
      }
    }

    @Override
    public String buildUsageLimitString(Context context) {
      String currentLicence = getCurrentLicence(context);
      return context.getString(R.string.dialog_contrib_usage_limit_synchronization, TRIAL_DURATION_DAYS, currentLicence);
    }
  },
  SPLIT_TEMPLATE(false, PROFESSIONAL) {
    @Override
    public String buildUsageLimitString(Context context) {
      String currentLicence = getCurrentLicence(context);
      return context.getString(R.string.dialog_contrib_usage_limit_split_templates, currentLicence);
    }
  };

  ContribFeature() {
    this(true);
  }

  ContribFeature(boolean hasTrial) {
    this(hasTrial, CONTRIB);
  }

  ContribFeature(boolean hasTrial, LicenceHandler.LicenceStatus licenceStatus) {
    this.hasTrial = hasTrial;
    this.licenceStatus = licenceStatus;
  }


  public static final int FREE_PLANS = 3;
  public static final int FREE_ACCOUNTS = 5;
  public static final int FREE_SPLIT_TEMPLATES = 1;

  private boolean hasTrial;
  private LicenceHandler.LicenceStatus licenceStatus;
  /**
   * how many times contrib features can be used for free
   */
  public static final int USAGES_LIMIT = 10;

  public String toString() {
    return name().toLowerCase(Locale.US);
  }

  public int getUsages() {
    return MyApplication.getInstance().getSettings()
        .getInt(getPrefKey(), 0);
  }

  /**
   * @return number of remaining usages (> 0, if usage still possible, <= 0 if not)
   */
  public int recordUsage() {
    if (!hasAccess()) {
      int usages = getUsages() + 1;
      MyApplication.getInstance().getSettings().edit()
          .putInt(getPrefKey(), usages)
          .apply();
      return USAGES_LIMIT - usages;
    }
    return USAGES_LIMIT;
  }

  private String getPrefKey() {
    return "FEATURE_USAGES_" + name();
  }

  public int usagesLeft() {
    return hasTrial ? USAGES_LIMIT - getUsages() : 0;
  }

  /**
   * @return if user has licence that includes feature
   */
  public boolean hasAccess() {
    if (BuildConfig.BUILD_TYPE.equals("beta")) {
      return true;
    }
    LicenceHandler licenceHandler = MyApplication.getInstance().getLicenceHandler();
    return licenceHandler.isEnabledFor(getLicenceStatus());
  }

  /**
   * @return user either has access through licence or through trial
   */
  public boolean isAvailable() {
    return hasAccess() || usagesLeft() > 0;
  }

  public String buildRequiresString(Context ctx) {
    return ctx.getString(R.string.contrib_key_requires, ctx.getString(getLicenceStatus().getResId()));
  }

  public int getLabelResIdOrThrow(Context ctx) {
    String name = "contrib_feature_" + toString() + "_label";
    int resId = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
    if (resId == 0) {
      throw new Resources.NotFoundException(name);
    }
    return resId;
  }

  public int getLimitReachedWarningResIdOrThrow(Context ctx) {
    String name = "warning_" + toString() + "_limit_reached";
    int resId = ctx.getResources().getIdentifier(name, "string", ctx.getPackageName());
    if (resId == 0) {
      throw new Resources.NotFoundException(name);
    }
    return resId;
  }

  public CharSequence buildFullInfoString(Context ctx) {
    return Html.fromHtml(ctx.getString(R.string.dialog_contrib_premium_feature,
        "<i>" + ctx.getString(getLabelResIdOrThrow(ctx)) + "</i>",
        ctx.getString(getLicenceStatus().getResId())) + " " +
        buildUsageLimitString(ctx));
  }

  @SuppressLint("DefaultLocale")
  public CharSequence buildUsagesLefString(Context ctx) {
    int usagesLeft = usagesLeft();
    return ctx.getText(R.string.dialog_contrib_usage_count) + " : " +
        String.format("%d/%d", usagesLeft, USAGES_LIMIT);
  }

  public String buildUsageLimitString(Context context) {
    String currentLicence = getCurrentLicence(context);
    return context.getString(R.string.dialog_contrib_usage_limit, USAGES_LIMIT, currentLicence);
  }

  @NonNull
  protected String getCurrentLicence(Context context) {
    LicenceHandler.LicenceStatus licenceStatus = MyApplication.getInstance().getLicenceHandler().getLicenceStatus();
    return context.getString(licenceStatus == null ? R.string.licence_status_free : licenceStatus.getResId());
  }

  public CharSequence buildRemoveLimitation(Context ctx, boolean asHTML) {
    int resId = R.string.dialog_contrib_reminder_remove_limitation;
    return asHTML ? ctx.getText(resId) : ctx.getString(resId);
  }

  public boolean isExtended() {
    return getLicenceStatus() == EXTENDED;
  }

  public boolean isProfessional() {
    return getLicenceStatus() == PROFESSIONAL;
  }

  public boolean hasTrial() {
    return hasTrial;
  }

  public LicenceHandler.LicenceStatus getLicenceStatus() {
    return licenceStatus;
  }

}