/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.totschnig.myexpenses.test.provider;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.ProviderTestCase2;
import android.test.mock.MockContentResolver;

import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.TransactionProvider;

/*
 */

/**
 * This class tests the content provider for the Note Pad sample application.
 * <p/>
 * To learn how to run an entire test package or one of its classes, please see
 * "Testing in Eclipse, with ADT" or "Testing in Other IDEs" in the Developer Guide.
 */
public class AccountTest extends ProviderTestCase2<TransactionProvider> {

  // Contains a reference to the mocked content resolver for the provider under test.
  private MockContentResolver mMockResolver;

  // Contains an SQLite database, used as test data
  private SQLiteDatabase mDb;

  // Contains the test data, as an array of NoteInfo instances.
  private final AccountInfo[] TEST_ACCOUNTS = {
      new AccountInfo("Account 0", AccountType.CASH, 0),
      new AccountInfo("Account 1", AccountType.BANK, 100),
      new AccountInfo("Account 2", AccountType.CCARD, -100),
  };

  /*
   * Constructor for the test case class.
   * Calls the super constructor with the class name of the provider under test and the
   * authority name of the provider.
   */
  public AccountTest() {
    super(TransactionProvider.class, TransactionProvider.AUTHORITY);
  }

  /*
   * Sets up the test environment before each test method. Creates a mock content resolver,
   * gets the provider under test, and creates a new database for the provider.
   */
  @Override
  protected void setUp() throws Exception {
    // Calls the base class implementation of this method.
    super.setUp();

    // Gets the resolver for this test.
    mMockResolver = getMockContentResolver();

        /*
         * Gets a handle to the database underlying the provider. Gets the provider instance
         * created in super.setUp(), gets the DatabaseOpenHelper for the provider, and gets
         * a database object from the helper.
         */
    mDb = getProvider().getOpenHelperForTest().getWritableDatabase();
  }

  /*
   * Sets up test data.
   * The test data is in an SQL database. It is created in setUp() without any data,
   * and populated in insertData if necessary.
   */
  private void insertData() {

    // Sets up test data
    for (AccountInfo TEST_ACCOUNT : TEST_ACCOUNTS) {
      // Adds a record to the database.
      mDb.insertOrThrow(
          DatabaseConstants.TABLE_ACCOUNTS,             // the table name for the insert
          null,      // column set to null if empty values map
          TEST_ACCOUNT.getContentValues()  // the values map to insert
      );
    }
  }


  /*
   * Tests the provider's public API for querying data in the table, using the URI for
   * a dataset of records.
   */
  public void testQueriesOnAccountUri() {
    // Defines a projection of column names to return for a query
    final String[] TEST_PROJECTION = {
        DatabaseConstants.KEY_LABEL, DatabaseConstants.KEY_DESCRIPTION, DatabaseConstants.KEY_CURRENCY
    };

    // Defines a selection column for the query. When the selection columns are passed
    // to the query, the selection arguments replace the placeholders.
    final String COMMENT_SELECTION = DatabaseConstants.KEY_LABEL + " = " + "?";

    // Defines the selection columns for a query.
    final String SELECTION_COLUMNS =
        COMMENT_SELECTION + " OR " + COMMENT_SELECTION + " OR " + COMMENT_SELECTION;

    // Defines the arguments for the selection columns.
    final String[] SELECTION_ARGS = {"Account 0", "Account 1", "Account 2"};

    // Defines a query sort order
    final String SORT_ORDER = DatabaseConstants.KEY_LABEL + " ASC";

    // Query subtest 1.
    // If there are no records in the table, the returned cursor from a query should be empty.
    Cursor cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI,  // the URI for the main data table
        null,                       // no projection, get all columns
        null,                       // no selection criteria, get all records
        null,                       // no selection arguments
        null                        // use default sort order
    );
    assert cursor != null;

    assertEquals(0, cursor.getCount());

    // Query subtest 2.
    // If the table contains records, the returned cursor from a query should contain records.

    // Inserts the test data into the provider's underlying data source
    insertData();
    cursor.close();

    // Gets all the columns for all the rows in the table
    cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI,  // the URI for the main data table
        null,                       // no projection, get all columns
        null,                       // no selection criteria, get all records
        null,                       // no selection arguments
        null                        // use default sort order
    );
    assert cursor != null;

    // Asserts that the returned cursor contains the same number of rows as the size of the
    // test data array + the default account
    assertEquals(TEST_ACCOUNTS.length, cursor.getCount());
    cursor.close();

    // Query subtest 3.
    // A query that uses a projection should return a cursor with the same number of columns
    // as the projection, with the same names, in the same order.
    Cursor projectionCursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI,  // the URI for the main data table
        TEST_PROJECTION,            // get the title, note, and mod date columns
        null,                       // no selection columns, get all the records
        null,                       // no selection criteria
        null                        // use default the sort order
    );

    // Asserts that the number of columns in the cursor is the same as in the projection
    assert projectionCursor != null;
    assertEquals(TEST_PROJECTION.length, projectionCursor.getColumnCount());

    // Asserts that the names of the columns in the cursor and in the projection are the same.
    // This also verifies that the names are in the same order.
    assertEquals(TEST_PROJECTION[0], projectionCursor.getColumnName(0));
    assertEquals(TEST_PROJECTION[1], projectionCursor.getColumnName(1));
    assertEquals(TEST_PROJECTION[2], projectionCursor.getColumnName(2));
    projectionCursor.close();

    // Query subtest 4
    // A query that uses selection criteria should return only those rows that match the
    // criteria. Use a projection so that it's easy to get the data in a particular column.
    projectionCursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI, // the URI for the main data table
        TEST_PROJECTION,           // get the title, note, and mod date columns
        SELECTION_COLUMNS,         // select on the title column
        SELECTION_ARGS,            // select titles "Note0", "Note1", or "Note5"
        SORT_ORDER                 // sort ascending on the title column
    );

    // Asserts that the cursor has the same number of rows as the number of selection arguments
    assert projectionCursor != null;
    assertEquals(SELECTION_ARGS.length, projectionCursor.getCount());

    int index = 0;

    while (projectionCursor.moveToNext()) {

      // Asserts that the selection argument at the current index matches the value of
      // the title column (column 0) in the current record of the cursor
      assertEquals(SELECTION_ARGS[index], projectionCursor.getString(0));

      index++;
    }

    // Asserts that the index pointer is now the same as the number of selection arguments, so
    // that the number of arguments tested is exactly the same as the number of rows returned.
    assertEquals(SELECTION_ARGS.length, index);
    projectionCursor.close();
  }

  /*
   * Tests queries against the provider, using the note id URI. This URI encodes a single
   * record ID. The provider should only return 0 or 1 record.
   */
  public void testQueriesOnAccountIdUri() {
    // Defines the selection column for a query. The "?" is replaced by entries in the
    // selection argument array
    final String SELECTION_COLUMNS = DatabaseConstants.KEY_LABEL + " = " + "?";

    // Defines the argument for the selection column.
    final String[] SELECTION_ARGS = {"Account 0"};

    // Creates a projection includes the row id column, so that account id can be retrieved.
    final String[] Account_ID_PROJECTION = {
        DatabaseConstants.KEY_ROWID,
        DatabaseConstants.KEY_LABEL};

    // Tests that a query against a table containing records returns a single record whose ID
    // is the one requested in the URI provided.

    // Inserts the test data into the provider's underlying data source.
    insertData();

    // We filter the default accountId out
    Cursor cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI, // the base URI for the table
        Account_ID_PROJECTION,
        null,
        null,
        null
    );
    assert cursor != null;

    // Asserts that the cursor contains the inserted rows
    assertEquals(TEST_ACCOUNTS.length, cursor.getCount());

    // Moves to the cursor's first row, and asserts that this did not fail.
    assertTrue(cursor.moveToFirst());

    // fetch the id of the first row
    int inputAccountId = cursor.getInt(0);

    // Builds a URI based on the provider's content ID URI base and the saved note ID.
    Uri AccountIdUri = ContentUris.withAppendedId(TransactionProvider.ACCOUNTS_URI, inputAccountId);

    cursor.close();

    // Queries the table using the content ID URI, which returns a single record with the
    // specified note ID, matching the selection criteria provided.
    cursor = mMockResolver.query(AccountIdUri, // the URI for a single note
        Account_ID_PROJECTION,                 // same projection, get ID and title columns
        SELECTION_COLUMNS,                  // same selection, based on title column
        SELECTION_ARGS,                     // same selection arguments, title = "Note1"
        null                          // same sort order returned, by title, ascending
    );
    assert cursor != null;

    // Asserts that the cursor contains only one row.
    assertEquals(1, cursor.getCount());

    // Moves to the cursor's first row, and asserts that this did not fail.
    assertTrue(cursor.moveToFirst());

    // Asserts that the note ID passed to the provider is the same as the note ID returned.
    assertEquals(inputAccountId, cursor.getInt(0));
    cursor.close();
  }

  /*
   *  Tests inserts into the data model.
   */
  public void testInserts() {
    // Does a full query on the table. Since insertData() hasn't yet been called, the
    // table should only contain the record just inserted.
    Cursor cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI, // the main table URI
        null,                      // no projection, return all the columns
        null,                      // no selection criteria, return all the rows in the model
        null,                      // no selection arguments
        null                       // default sort order
    );
    assert cursor != null;

    assertEquals(0, cursor.getCount());

    // Creates a new Account instance
    AccountInfo account = new AccountInfo(
        "Account 4",
        AccountType.ASSET, 1000);

    // Insert subtest 1.
    // Inserts a row using the new note instance.
    // No assertion will be done. The insert() method either works or throws an Exception
    Uri rowUri = mMockResolver.insert(
        TransactionProvider.ACCOUNTS_URI,  // the main table URI
        account.getContentValues()     // the map of values to insert as a new record
    );

    // Parses the returned URI to get the note ID of the new note. The ID is used in subtest 2.
    long AccountId = ContentUris.parseId(rowUri);
    cursor.close();

    // Does a full query on the table. Since insertData() hasn't yet been called, the
    // table should only contain the record just inserted.
    cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI, // the main table URI
        null,                      // no projection, return all the columns
        null,                      // no selection criteria, return all the rows in the model
        null,                      // no selection arguments
        null                       // default sort order
    );
    assert cursor != null;

    // now there is 1
    assertEquals(1, cursor.getCount());

    // Moves to the first (and only) record in the cursor and asserts that this worked.
    assertTrue(cursor.moveToFirst());

    // Since no projection was used, get the column indexes of the returned columns
    int descriptionIndex = cursor.getColumnIndex(DatabaseConstants.KEY_DESCRIPTION);
    int labelIndex = cursor.getColumnIndex(DatabaseConstants.KEY_LABEL);
    int balanceIndex = cursor.getColumnIndex(DatabaseConstants.KEY_OPENING_BALANCE);
    int currencyIndex = cursor.getColumnIndex(DatabaseConstants.KEY_CURRENCY);

    // Tests each column in the returned cursor against the data that was inserted, comparing
    // the field in the NoteInfo object to the data at the column index in the cursor.
    assertEquals(account.label, cursor.getString(labelIndex));
    assertEquals(account.getDescription(), cursor.getString(descriptionIndex));
    assertEquals(account.openingBalance, cursor.getLong(balanceIndex));
    assertEquals(account.currency, cursor.getString(currencyIndex));
    // Insert subtest 2.
    // Tests that we can't insert a record whose id value already exists.

    // Defines a ContentValues object so that the test can add a note ID to it.
    ContentValues values = account.getContentValues();

    // Adds the note ID retrieved in subtest 1 to the ContentValues object.
    values.put(DatabaseConstants.KEY_ROWID, AccountId);

    // Tries to insert this record into the table.
    //Our content provider returns null on failed insert
    try {
      mMockResolver.insert(TransactionProvider.ACCOUNTS_URI, values);
      fail("Expected insert failure for existing record but insert succeeded.");
    } catch (Exception e) {
      // succeeded, do nothing
    }
    cursor.close();
  }

  /*
   * Tests deletions from the data model.
   */
  public void testDeletes() {
    // Subtest 1.
    // Tries to delete a record from a data model that is empty.

    // Sets the selection column to "title"
    final String SELECTION_COLUMNS = DatabaseConstants.KEY_LABEL + " = " + "?";

    // Sets the selection argument "Note0"
    final String[] SELECTION_ARGS = {"Account 0"};

    // Tries to delete rows matching the selection criteria from the data model.
    int rowsDeleted = mMockResolver.delete(
        TransactionProvider.ACCOUNTS_URI, // the base URI of the table
        SELECTION_COLUMNS,         // select based on the title column
        SELECTION_ARGS             // select title = "Note0"
    );

    // Assert that the deletion did not work. The number of deleted rows should be zero.
    assertEquals(0, rowsDeleted);

    // Subtest 2.
    // Tries to delete an existing record. Repeats the previous subtest, but inserts data first.

    // Inserts data into the model.
    insertData();

    // Uses the same parameters to try to delete the row with title "Note0"
    rowsDeleted = mMockResolver.delete(
        TransactionProvider.ACCOUNTS_URI, // the base URI of the table
        SELECTION_COLUMNS,         // same selection column, "title"
        SELECTION_ARGS             // same selection arguments, title = "Note0"
    );

    // The number of deleted rows should be 1.
    assertEquals(1, rowsDeleted);

    // Tests that the record no longer exists. Tries to get it from the table, and
    // asserts that nothing was returned.

    // Queries the table with the same selection column and argument used to delete the row.
    Cursor cursor = mMockResolver.query(
        TransactionProvider.ACCOUNTS_URI, // the base URI of the table
        null,                      // no projection, return all columns
        SELECTION_COLUMNS,         // select based on the title column
        SELECTION_ARGS,            // select title = "Note0"
        null                       // use the default sort order
    );
    assert cursor != null;

    // Asserts that the cursor is empty since the record had already been deleted.
    assertEquals(0, cursor.getCount());
    cursor.close();
  }

  /*
   * Tests updates to the data model.
   */
  public void testUpdates() {
    // Selection column for identifying a record in the data model.
    final String SELECTION_COLUMNS = DatabaseConstants.KEY_LABEL + " = " + "?";

    // Selection argument for the selection column.
    final String[] selectionArgs = {"Account 1"};

    // Defines a map of column names and values
    ContentValues values = new ContentValues();

    // Subtest 1.
    // Tries to update a record in an empty table.

    // Sets up the update by putting the "note" column and a value into the values map.
    values.put(DatabaseConstants.KEY_LABEL, "Testing an update with this string");

    // Tries to update the table
    int rowsUpdated = mMockResolver.update(
        TransactionProvider.ACCOUNTS_URI,  // the URI of the data table
        values,                     // a map of the updates to do (column title and value)
        SELECTION_COLUMNS,           // select based on the title column
        selectionArgs               // select "title = Note1"
    );

    // Asserts that no rows were updated.
    assertEquals(0, rowsUpdated);

    // Subtest 2.
    // Builds the table, and then tries the update again using the same arguments.

    // Inserts data into the model.
    insertData();

    //  Does the update again, using the same arguments as in subtest 1.
    rowsUpdated = mMockResolver.update(
        TransactionProvider.ACCOUNTS_URI,   // The URI of the data table
        values,                      // the same map of updates
        SELECTION_COLUMNS,            // same selection, based on the title column
        selectionArgs                // same selection argument, to select "title = Note1"
    );

    // Asserts that only one row was updated. The selection criteria evaluated to
    // "title = Note1", and the test data should only contain one row that matches that.
    assertEquals(1, rowsUpdated);
  }
}
