package org.totschnig.myexpenses.test.espresso;

import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.support.test.espresso.matcher.CursorMatchers;
import android.support.test.filters.FlakyTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.model.CurrencyUnit;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.testutils.BaseUiTest;

import java.util.Currency;

import static android.support.test.espresso.Espresso.closeSoftKeyboard;
import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.testutils.Matchers.withAdaptedData;


@RunWith(AndroidJUnit4.class)
public final class MyExpensesCabTest extends BaseUiTest {

  @Rule
  public ActivityTestRule<MyExpenses> mActivityRule =
      new ActivityTestRule<>(MyExpenses.class, true, false);
  private Account account;

  @Before
  public void fixture() {
    account = new Account("Test account 1", CurrencyUnit.create(Currency.getInstance("EUR")), 0, "",
        AccountType.CASH, Account.DEFAULT_COLOR);
    account.save();
    Transaction op0 = Transaction.getNewInstance(account.getId());
    op0.setAmount(new Money(CurrencyUnit.create(Currency.getInstance("USD")), -1200L));
    op0.save();
    int times = 5;
    for (int i = 0; i < times; i++) {
      op0.saveAsNew();
    }
    Intent i = new Intent();
    i.putExtra(KEY_ROWID, account.getId());
    mActivityRule.launchActivity(i);
  }

  @After
  public void tearDown() throws RemoteException, OperationApplicationException {
    Account.delete(account.getId());
  }

  @Test
  @FlakyTest
  public void cloneCommandIncreasesListSize() {
    int origListSize = waitForAdapter().getCount();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1)
        .perform(longClick());
    performContextMenuClick(R.string.menu_clone_transaction, R.id.CLONE_TRANSACTION_COMMAND);
    onView(withId(R.id.SAVE_COMMAND)).perform(click());
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize + 1);
  }

  @Test
  @FlakyTest
  public void editCommandKeepsListSize() {
    int origListSize = waitForAdapter().getCount();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1) // position 0 is header
        .perform(longClick());
    performContextMenuClick(R.string.menu_edit, R.id.EDIT_COMMAND);
    onView(withId(R.id.SAVE_COMMAND)).perform(click());
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize);
    }

  @Test
  public void createTemplateCommandCreatesTemplate() {
    waitForAdapter();
    String templateTitle = "Espresso Template Test";
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1)
        .perform(longClick());
    performContextMenuClick(R.string.menu_create_template_from_transaction, R.id.CREATE_TEMPLATE_COMMAND);
    onView(withText(containsString(mActivityRule.getActivity().getString(R.string.dialog_title_template_title))))
        .check(matches(isDisplayed()));
    onView(withId(R.id.editText))
        .perform(typeText(templateTitle));
    closeSoftKeyboard();
    onView(withText(R.string.dialog_button_add)).perform(click());
    onView(withId(R.id.SAVE_COMMAND)).perform(click());

    //((EditText) mSolo.getView(EditText.class, 0)).onEditorAction(EditorInfo.IME_ACTION_DONE);
    onView(withId(R.id.MANAGE_PLANS_COMMAND)).perform(click());
    onView(withText(is(templateTitle))).check(matches(isDisplayed()));
  }

  @Test
  public void deleteCommandDecreasesListSize() {
    int origListSize = waitForAdapter().getCount();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1)
        .perform(longClick());
    performContextMenuClick(R.string.menu_delete, R.id.DELETE_COMMAND);
    onView(withText(R.string.menu_delete)).perform(click());
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize - 1);
  }

  @Test
  public void deleteCommandWithVoidOption() {
    int origListSize = waitForAdapter().getCount();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1) // position 0 is header
        .perform(longClick());
    performContextMenuClick(R.string.menu_delete, R.id.DELETE_COMMAND);
    onView(withId(R.id.checkBox)).perform(click());
    onView(withText(R.string.menu_delete)).perform(click());
    onData(is(instanceOf(Cursor.class))).inAdapterView(getWrappedList()).atPosition(1)
        .check(matches(hasDescendant(both(withId(R.id.voidMarker)).and(isDisplayed()))));
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize);
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1) // position 0 is header
        .perform(longClick());
    performContextMenuClick(R.string.menu_delete, R.id.UNDELETE_COMMAND);
    onView(getWrappedList())
        .check(matches(not(withAdaptedData(CursorMatchers.withRowString(DatabaseConstants.KEY_CR_STATUS, "VOID")))));
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize);
  }

  @Test
  public void deleteCommandCancelKeepsListSize() {
    int origListSize = waitForAdapter().getCount();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1)
        .perform(longClick());
    performContextMenuClick(R.string.menu_delete, R.id.DELETE_COMMAND);
    onView(withText(android.R.string.cancel)).perform(click());
    assertThat(waitForAdapter().getCount()).isEqualTo(origListSize);
  }

  @Test
  public void splitCommandCreatesSplitTransaction() throws InterruptedException {
    waitForAdapter();
    onData(is(instanceOf(Cursor.class)))
        .inAdapterView(getWrappedList())
        .atPosition(1)
        .perform(longClick());
    performContextMenuClick(R.string.menu_split_transaction, R.id.SPLIT_TRANSACTION_COMMAND);
    handleContribDialog(ContribFeature.SPLIT_TRANSACTION);
    onView(withText(R.string.menu_split_transaction)).perform(click());
    onView(withText(R.string.split_transaction)).check(matches(isDisplayed()));
    //CursoMatchers class does not allow to distinguish between null and 0 in database
/*    onData(CursorMatchers.withRowLong(DatabaseConstants.KEY_CATID, DatabaseConstants.SPLIT_CATID))
        .inAdapterView(allOf(
            isAssignableFrom(AdapterView.class),
            isDescendantOfA(withId(R.id.list)),
            isDisplayed())).check(matches(isDisplayed()));*/
  }

  @Override
  protected ActivityTestRule<? extends ProtectedFragmentActivity> getTestRule() {
    return mActivityRule;
  }
}
