package org.totschnig.myexpenses.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.provider.filter.AmountCriteria;
import org.totschnig.myexpenses.provider.filter.WhereFilter;
import org.totschnig.myexpenses.util.UiUtils;
import org.totschnig.myexpenses.util.Utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Currency;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;

public class AmountFilterDialog extends CommitSafeDialogFragment implements OnClickListener {
  private EditText mAmount1Text;
  private EditText mAmount2Text;
  private DecimalFormat nfDLocal;
  private Spinner mOperatorSpinner;
  public static final AmountFilterDialog newInstance(Currency currency) {
    Bundle bundle = new Bundle();
    bundle.putSerializable(KEY_CURRENCY, currency);
    AmountFilterDialog f = new AmountFilterDialog();
    f.setArguments(bundle);
    return f;
  }
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    MyExpenses ctx  = (MyExpenses) getActivity();
    LayoutInflater li = ctx.getLayoutInflater();
    //noinspection InflateParams
    View view = li.inflate(R.layout.filter_amount, null);
    mOperatorSpinner = (Spinner) view.findViewById(R.id.Operator);
    final View amount2Row = view.findViewById(R.id.Amount2Row);
    mOperatorSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

      @Override
      public void onItemSelected(AdapterView<?> parent, View view,
                                 int position, long id) {
        String selectedOp = getResources().getStringArray(R.array.comparison_operator_values)[position];
        amount2Row.setVisibility(selectedOp.equals("BTW") ? View.VISIBLE : View.GONE);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
      }
    });
    ((ArrayAdapter) mOperatorSpinner.getAdapter())
        .setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
    char decimalSeparator = Utils.getDefaultDecimalSeparator();
    DecimalFormatSymbols symbols = new DecimalFormatSymbols();
    symbols.setDecimalSeparator(decimalSeparator);
    nfDLocal = new DecimalFormat("#0.###",symbols);
    nfDLocal.setGroupingUsed(false);
    mAmount1Text = (EditText) view.findViewById(R.id.amount1);
    mAmount2Text = (EditText) view.findViewById(R.id.amount2);
    int fractionDigits = Money.getFractionDigits(
        (Currency) getArguments().getSerializable(KEY_CURRENCY));
    UiUtils.configDecimalSeparator(mAmount1Text, decimalSeparator, fractionDigits);
    UiUtils.configDecimalSeparator(mAmount2Text, decimalSeparator, fractionDigits);

    return new AlertDialog.Builder(ctx)
      .setTitle(R.string.search_amount)
      .setView(view)
      .setPositiveButton(android.R.string.ok,this)
      .setNegativeButton(android.R.string.cancel,null)
      .create();
  }
  @Override
  public void onClick(DialogInterface dialog, int which) {
    MyExpenses ctx = (MyExpenses) getActivity();
    if (ctx==null) {
      return;
    }
    String strAmount1 = mAmount1Text.getText().toString();
    String strAmount2 = mAmount2Text.getText().toString();
    BigDecimal bdAmount1,bdAmount2 = null;
    String selectedOp = getResources().getStringArray(R.array.comparison_operator_values)
        [mOperatorSpinner.getSelectedItemPosition()];
    if (strAmount1.equals("")) {
      return;
    }
    Currency currency = (Currency)getArguments().getSerializable(KEY_CURRENCY);
    AlertDialog dlg = (AlertDialog) dialog;
    boolean type = ((RadioGroup) dlg.findViewById(R.id.type)).getCheckedRadioButtonId() == R.id.income;
    bdAmount1 = Utils.validateNumber(nfDLocal, strAmount1);
    if (selectedOp.equals("BTW")) {
      if (strAmount2.equals("")) {
        return;
      }
      bdAmount2 = Utils.validateNumber(nfDLocal, strAmount2);
    }
    ctx.addFilterCriteria(R.id.FILTER_AMOUNT_COMMAND,new AmountCriteria(
        WhereFilter.Operation.valueOf(selectedOp),
        currency,
        type,
        bdAmount1,bdAmount2));
  }
}
