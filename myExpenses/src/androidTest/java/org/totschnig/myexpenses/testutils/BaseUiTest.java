package org.totschnig.myexpenses.testutils;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ListView;

import org.hamcrest.Matcher;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.util.Utils;

import androidx.fragment.app.Fragment;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.totschnig.myexpenses.testutils.Espresso.openActionBarOverflowOrOptionsMenu;

public abstract class BaseUiTest {
  protected void clickOnFirstListEntry() {
    clickOnListEntry(0);
  }

  protected void clickOnWrappedListEntry(Matcher<Object> dataMatcher) {
    waitForAdapter();
    onData(dataMatcher).inAdapterView(getWrappedList()).atPosition(0).perform(click());
  }

  private void clickOnListEntry(int atPosition) {
    waitForAdapter();
    onData(anything()).inAdapterView(isAssignableFrom(AdapterView.class)).atPosition(atPosition).perform(click());
  }


  /**
   * Click on a menu item, that might be visible or hidden in overflow menu
   *
   * @param menuItemId
   * @param menuTextResId
   */
  protected void clickMenuItem(int menuItemId, int menuTextResId) {
    try {
      onView(withId(menuItemId)).perform(click());
    } catch (NoMatchingViewException e) {
      openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getInstrumentation().getTargetContext());
      onView(withText(menuTextResId)).perform(click());
    }
  }

  protected Matcher<View> getWrappedList() {
    return allOf(
        isAssignableFrom(AdapterView.class),
        isDescendantOfA(withId(R.id.list)),
        isDisplayed());
  }

  /**
   * @param legacyString String used on Gingerbread where context actions are rendered in a context menu
   * @param cabId        id of menu item rendered in CAB on Honeycomb and higher
   */
  protected void performContextMenuClick(int legacyString, int cabId) {
    onView(Utils.hasApiLevel(Build.VERSION_CODES.HONEYCOMB) ? withId(cabId) : withText(legacyString))
        .perform(click());
  }

  protected void handleContribDialog(ContribFeature contribFeature) throws InterruptedException {
    if (!contribFeature.hasAccess()) {
      onView(withText(R.string.dialog_title_contrib_feature)).check(matches(isDisplayed()));
      //OpenIAB on emulator displays a toast that is messing with our expectations, we wait for it to disappear
      Thread.sleep(3500);
      onView(withText(R.string.dialog_contrib_no)).perform(scrollTo()).perform(click());
    }
  }

  protected abstract ActivityTestRule<? extends ProtectedFragmentActivity> getTestRule();


  private ViewGroup getList() {
    Fragment currentFragment = getTestRule().getActivity().getCurrentFragment();
    if (currentFragment == null) return null;
    return (ViewGroup) currentFragment.getView().findViewById(getListId());
  }

  protected int getListId() {
    return R.id.list;
  }

  private Adapter getAdapter() {
    ViewGroup list = getList();
    if (list == null) return null;
    if (list instanceof StickyListHeadersListView) {
      return ((StickyListHeadersListView) list).getAdapter();
    }
    if (list instanceof ListView) {
      return ((ListView) list).getAdapter();
    }
    return null;
  }

  protected Adapter waitForAdapter() {
    while (true) {
      Adapter adapter = getAdapter();
      try {
        Thread.sleep(500);
      } catch (InterruptedException ignored) {
      }
      if (adapter != null) {
        return adapter;
      }
    }
  }

}
