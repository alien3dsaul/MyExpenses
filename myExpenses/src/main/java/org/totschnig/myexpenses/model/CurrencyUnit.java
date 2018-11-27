package org.totschnig.myexpenses.model;

import com.android.setupwizardlib.annotations.VisibleForTesting;
import com.google.auto.value.AutoValue;

import java.io.Serializable;
import java.util.Currency;

@AutoValue
public abstract class CurrencyUnit implements Serializable {
  public static CurrencyUnit create(Currency currency) {
    return create(currency.getCurrencyCode(), currency.getSymbol(), currency.getDefaultFractionDigits());
  }
  public static CurrencyUnit create(String code, String symbol, int fractiondigits) {
    return new AutoValue_CurrencyUnit(code, symbol, fractiondigits);
  }

  public abstract String code();
  public abstract String symbol();
  public abstract int fractionDigits();
}
