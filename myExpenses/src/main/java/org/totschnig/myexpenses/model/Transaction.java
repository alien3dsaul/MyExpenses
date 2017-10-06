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

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.RemoteException;
import android.support.annotation.StringRes;

import org.apache.commons.lang3.StringUtils;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.AppDirHelper;
import org.totschnig.myexpenses.util.CurrencyFormatter;
import org.totschnig.myexpenses.util.FileCopyUtils;
import org.totschnig.myexpenses.util.PictureDirHelper;
import org.totschnig.myexpenses.util.TextUtils;
import org.totschnig.myexpenses.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

import static org.totschnig.myexpenses.provider.DatabaseConstants.DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.FULL_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.IS_SAME_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ACCOUNT_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CATID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COLOR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COMMENT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CR_STATUS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DATE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_INSTANCEID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_IS_SAME_CURRENCY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_METHODID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_METHOD_LABEL;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MONTH;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEEID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEE_NAME;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PICTURE_URI;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_REFERENCE_NUMBER;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_STATUS;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TEMPLATEID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_WEEK;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_THIS_YEAR_OF_WEEK_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSACTIONID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_ACCOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_TRANSFER_PEER;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_UUID;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_WEEK;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_WEEK_END;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_WEEK_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR_OF_MONTH_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_YEAR_OF_WEEK_START;
import static org.totschnig.myexpenses.provider.DatabaseConstants.LABEL_MAIN;
import static org.totschnig.myexpenses.provider.DatabaseConstants.LABEL_SUB;
import static org.totschnig.myexpenses.provider.DatabaseConstants.STATUS_NONE;
import static org.totschnig.myexpenses.provider.DatabaseConstants.STATUS_UNCOMMITTED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.THIS_DAY;
import static org.totschnig.myexpenses.provider.DatabaseConstants.THIS_YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TRANSFER_AMOUNT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.TRANSFER_PEER_PARENT;
import static org.totschnig.myexpenses.provider.DatabaseConstants.VIEW_UNCOMMITTED;
import static org.totschnig.myexpenses.provider.DatabaseConstants.YEAR;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getMonth;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getThisWeek;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getThisYearOfWeekStart;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getWeek;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getWeekEnd;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getWeekStart;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfMonthStart;
import static org.totschnig.myexpenses.provider.DatabaseConstants.getYearOfWeekStart;
import static org.totschnig.myexpenses.provider.DbUtils.getLongOrNull;

/**
 * Domain class for transactions
 *
 * @author Michael Totschnig
 */
public class Transaction extends Model {
  public static final int TYPE_TRANSACTION = 0;
  public static final int TYPE_TRANSFER = 1;
  public static final int TYPE_SPLIT = 2;
  protected boolean inEditState = false;
  private String comment = "";
  private String payee = "";
  private String referenceNumber = "";
  private String label = "";
  private Date date;
  private Money amount;
  private Money transferAmount;
  private Long catId;
  private long accountId;
  private Long methodId;
  private String methodLabel = "";
  private Long parentId = null;
  private Long payeeId = null;

  private Plan initialPlan;

  public void setInitialPlan(Plan initialPlan) {
    this.initialPlan = initialPlan;
  }


  /**
   * template which defines the plan for which this transaction has been created
   */
  public Template originTemplate = null;
  /**
   * id of an instance of the event (plan) for which this transaction has been created
   */
  public Long originPlanInstanceId = null;
  /**
   * 0 = is normal, special states are
   * {@link org.totschnig.myexpenses.provider.DatabaseConstants#STATUS_EXPORTED} and
   * {@link org.totschnig.myexpenses.provider.DatabaseConstants#STATUS_UNCOMMITTED}
   */
  public int status = 0;
  public static String[] PROJECTION_BASE, PROJECTION_EXTENDED, PROJECTION_EXTENDED_AGGREGATE;

  static {
    buildProjection();
  }

  public static void buildProjection() {
    PROJECTION_BASE = new String[]{
        KEY_ROWID,
        KEY_DATE,
        KEY_AMOUNT,
        KEY_COMMENT,
        KEY_CATID,
        LABEL_MAIN,
        LABEL_SUB,
        KEY_PAYEE_NAME,
        KEY_TRANSFER_PEER,
        KEY_TRANSFER_ACCOUNT,
        KEY_METHODID,
        KEY_METHOD_LABEL,
        KEY_CR_STATUS,
        KEY_REFERENCE_NUMBER,
        KEY_PICTURE_URI,
        getYearOfWeekStart() + " AS " + KEY_YEAR_OF_WEEK_START,
        getYearOfMonthStart() + " AS " + KEY_YEAR_OF_MONTH_START,
        YEAR + " AS " + KEY_YEAR,
        getMonth() + " AS " + KEY_MONTH,
        getWeek() + " AS " + KEY_WEEK,
        DAY + " AS " + KEY_DAY,
        getThisYearOfWeekStart() + " AS " + KEY_THIS_YEAR_OF_WEEK_START,
        THIS_YEAR + " AS " + KEY_THIS_YEAR,
        getThisWeek() + " AS " + KEY_THIS_WEEK,
        THIS_DAY + " AS " + KEY_THIS_DAY,
        getWeekStart() + " AS " + KEY_WEEK_START,
        getWeekEnd() + " AS " + KEY_WEEK_END
    };

    //extended
    int baseLength = PROJECTION_BASE.length;
    PROJECTION_EXTENDED = new String[baseLength + 4];
    System.arraycopy(PROJECTION_BASE, 0, PROJECTION_EXTENDED, 0, baseLength);
    PROJECTION_EXTENDED[baseLength] = KEY_COLOR;
    //the definition of column TRANSFER_PEER_PARENT refers to view_extended,
    //thus can not be used in PROJECTION_BASE
    PROJECTION_EXTENDED[baseLength + 1] = TRANSFER_PEER_PARENT + " AS transfer_peer_parent";
    PROJECTION_EXTENDED[baseLength + 2] = KEY_STATUS;
    PROJECTION_EXTENDED[baseLength + 3] = KEY_ACCOUNT_LABEL;

    //extended for aggregate include is_same_currecny
    int extendedLength = baseLength + 4;
    PROJECTION_EXTENDED_AGGREGATE = new String[extendedLength + 1];
    System.arraycopy(PROJECTION_EXTENDED, 0, PROJECTION_EXTENDED_AGGREGATE, 0, extendedLength);
    PROJECTION_EXTENDED_AGGREGATE[extendedLength] = IS_SAME_CURRENCY + " AS " + KEY_IS_SAME_CURRENCY;
  }

  public static final Uri CONTENT_URI = TransactionProvider.TRANSACTIONS_URI;
  public static final Uri EXTENDED_URI = CONTENT_URI.buildUpon().appendQueryParameter(
      TransactionProvider.QUERY_PARAMETER_EXTENDED, "1").build();
  public static final Uri CALLER_IS_SYNC_ADAPTER_URI = CONTENT_URI.buildUpon()
      .appendQueryParameter(TransactionProvider.QUERY_PARAMETER_CALLER_IS_SYNCADAPTER, "1").build();

  public Money getAmount() {
    return amount;
  }

  public void setAmount(Money amount) {
    this.amount = amount;
  }

  public Money getTransferAmount() {
    return transferAmount;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }

  public Long getMethodId() {
    return methodId;
  }

  public void setMethodId(Long methodId) {
    this.methodId = methodId;
  }

  public String getMethodLabel() {
    return methodLabel;
  }

  public void setMethodLabel(String methodLabel) {
    this.methodLabel = methodLabel;
  }

  public String getPayee() {
    return payee;
  }

  public Long getPayeeId() {
    return payeeId;
  }

  public void setPayeeId(Long payeeId) {
    this.payeeId = payeeId;
  }

  /**
   * stores a short label of the category or the account the transaction is linked to
   */
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void setTransferAmount(Money transferAmount) {
    this.transferAmount = transferAmount;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public void setTransferAccountId(Long transferAccountId) {
    //noop, convenience that allows to set transfer account on template and transfer without cast
  }

  public Long getTransferAccountId() {
    return null; //convenience that allows to set transfer account on template and transfer without cast
  }

  public enum CrStatus {
    UNRECONCILED(Color.GRAY, ""), CLEARED(Color.BLUE, "*"), RECONCILED(Color.GREEN, "X"), VOID(Color.RED, null);
    public int color;
    public String symbol;

    CrStatus(int color, String symbol) {
      this.color = color;
      this.symbol = symbol;
    }

    public static final String JOIN;

    static {
      JOIN = TextUtils.joinEnum(CrStatus.class);
    }

    public static CrStatus fromQifName(String qifName) {
      if (qifName == null)
        return UNRECONCILED;
      if (qifName.equals("*")) {
        return CLEARED;
      } else if (qifName.equalsIgnoreCase("X")) {
        return RECONCILED;
      } else {
        return UNRECONCILED;
      }
    }

    @StringRes
    public int toStringRes() {
      switch (this) {
        case CLEARED:
          return R.string.status_cleared;
        case RECONCILED:
          return R.string.status_reconciled;
        case UNRECONCILED:
          return R.string.status_uncreconciled;
        case VOID:
          return R.string.status_void;
      }
      return 0;
    }
  }

  public CrStatus crStatus;
  transient protected Uri pictureUri;

  /**
   * factory method for retrieving an instance from the db with the given id
   *
   * @param id
   * @return instance of {@link Transaction} or {@link Transfer} or null if not found
   */
  public static Transaction getInstanceFromDb(long id) {
    Transaction t;
    String[] projection = new String[]{KEY_ROWID, KEY_DATE, KEY_AMOUNT, KEY_COMMENT, KEY_CATID,
        FULL_LABEL, KEY_PAYEEID, KEY_PAYEE_NAME, KEY_TRANSFER_PEER, KEY_TRANSFER_ACCOUNT,
        KEY_ACCOUNTID, KEY_METHODID, KEY_PARENTID, KEY_CR_STATUS, KEY_REFERENCE_NUMBER, KEY_CURRENCY,
        KEY_PICTURE_URI, KEY_METHOD_LABEL, KEY_STATUS, TRANSFER_AMOUNT, KEY_TEMPLATEID, KEY_UUID};

    Cursor c = cr().query(
        EXTENDED_URI.buildUpon().appendPath(String.valueOf(id)).build(), projection, null, null, null);
    if (c == null) {
      return null;
    }
    if (c.getCount() == 0) {
      c.close();
      return null;
    }
    c.moveToFirst();
    Long transfer_peer = getLongOrNull(c, KEY_TRANSFER_PEER);
    long account_id = c.getLong(c.getColumnIndexOrThrow(KEY_ACCOUNTID));
    long amount = c.getLong(c.getColumnIndexOrThrow(KEY_AMOUNT));
    Money money = new Money(Utils.getSaveInstance(DbUtils.getString(c, KEY_CURRENCY)), amount);
    Long parent_id = getLongOrNull(c, KEY_PARENTID);
    Long catId = getLongOrNull(c, KEY_CATID);
    if (transfer_peer != null) {
      Transfer transfer = new Transfer(account_id, money, parent_id);
      transfer.setTransferPeer(transfer_peer);
      Long transferAccountId = getLongOrNull(c, KEY_TRANSFER_ACCOUNT);
      transfer.setTransferAccountId(transferAccountId);
      transfer.setTransferAmount(new Money(Account.getInstanceFromDb(transferAccountId).currency,
          c.getLong(c.getColumnIndex(KEY_TRANSFER_AMOUNT))));
      t = transfer;
    } else {
      if (DatabaseConstants.SPLIT_CATID.equals(catId)) {
        t = new SplitTransaction(account_id, money);
      } else {
        t = new Transaction(account_id, money, parent_id);
      }
    }
    try {
      t.crStatus = CrStatus.valueOf(c.getString(c.getColumnIndexOrThrow(KEY_CR_STATUS)));
    } catch (IllegalArgumentException ex) {
      t.crStatus = CrStatus.UNRECONCILED;
    }
    t.setMethodId(getLongOrNull(c, KEY_METHODID));
    t.setMethodLabel(DbUtils.getString(c, KEY_METHOD_LABEL));
    t.setCatId(catId);
    t.setPayeeId(getLongOrNull(c, KEY_PAYEEID));
    t.setPayee(DbUtils.getString(c, KEY_PAYEE_NAME));
    t.setId(id);
    t.setDate(c.getLong(
        c.getColumnIndexOrThrow(KEY_DATE)) * 1000L);
    t.setComment(DbUtils.getString(c, KEY_COMMENT));
    t.setReferenceNumber(DbUtils.getString(c, KEY_REFERENCE_NUMBER));
    t.setLabel(DbUtils.getString(c, KEY_LABEL));

    int pictureUriColumnIndex = c.getColumnIndexOrThrow(KEY_PICTURE_URI);
    if (!c.isNull(pictureUriColumnIndex)) {
      Uri parsedUri = Uri.parse(c.getString(pictureUriColumnIndex));
      if("file".equals(parsedUri.getScheme())) { // Upgrade from legacy uris
        try {
          parsedUri = AppDirHelper.getContentUriForFile(new File(parsedUri.getPath()));
        } catch (IllegalArgumentException ignored) {}
      }
      t.setPictureUri(parsedUri);
    }

    t.status = c.getInt(c.getColumnIndexOrThrow(KEY_STATUS));
    Long originTemplateId = getLongOrNull(c, KEY_TEMPLATEID);
    t.originTemplate = originTemplateId == null ? null : Template.getInstanceFromDb(originTemplateId);
    t.uuid = DbUtils.getString(c, KEY_UUID);
    c.close();
    return t;
  }

  public static Transaction getInstanceFromTemplate(long id) {
    Template te = Template.getInstanceFromDb(id);
    return te == null ? null : getInstanceFromTemplate(te);
  }

  public static Transaction getInstanceFromTemplate(Template te) {
    Transaction tr;
    switch(te.operationType()) {
      case TYPE_TRANSACTION:
        tr = new Transaction(te.getAccountId(), te.getAmount());
        tr.setMethodId(te.getMethodId());
        tr.setMethodLabel(te.getMethodLabel());
        tr.setCatId(te.getCatId());
        break;
      case TYPE_TRANSFER:
        tr = new Transfer(te.getAccountId(), te.getAmount());
        tr.setTransferAccountId(te.getTransferAccountId());
        break;
      case TYPE_SPLIT:
        tr = new SplitTransaction(te.getAccountId(), te.getAmount());
        tr.status = STATUS_UNCOMMITTED;
        tr.setMethodId(te.getMethodId());
        tr.setMethodLabel(te.getMethodLabel());
        break;
      default:
        throw new IllegalStateException(
            String.format(Locale.ROOT, "Unknown type %d", te.operationType()));
    }
    tr.setComment(te.getComment());
    tr.setPayee(te.getPayee());
    tr.setLabel(te.getLabel());
    tr.originTemplate = te;
    if (tr instanceof SplitTransaction) {
      ((SplitTransaction) tr).persistForEdit();
      Cursor c = cr().query(Template.CONTENT_URI, new String[]{KEY_ROWID},
          KEY_PARENTID + " = ?", new String[]{String.valueOf(te.getId())}, null);
      if (c != null) {
        c.moveToFirst();
        while (!c.isAfterLast()) {
          Transaction part = Transaction.getInstanceFromTemplate(c.getLong(c.getColumnIndex(KEY_ROWID)));
          if (part != null) {
            part.status = STATUS_UNCOMMITTED;
            part.setParentId(tr.getId());
            part.saveAsNew();
          }
          c.moveToNext();
        }
        c.close();
      }
    }
    cr().update(
        TransactionProvider.TEMPLATES_URI
            .buildUpon()
            .appendPath(String.valueOf(te.getId()))
            .appendPath(TransactionProvider.URI_SEGMENT_INCREASE_USAGE)
            .build(),
        null, null, null);
    return tr;
  }

  /**
   * factory method for creating an object of the correct type and linked to a given account
   *
   * @param accountId the account the transaction belongs to if account no longer exists {@link Account#getInstanceFromDb(long) is called with 0}
   * @return instance of {@link Transaction} or {@link Transfer} or {@link SplitTransaction} with date initialized to current date
   */
  public static Transaction getNewInstance(long accountId) {
    return getNewInstance(accountId, null);
  }

  public static Transaction getNewInstance(long accountId, Long parentId) {
    Account account = Account.getInstanceFromDbWithFallback(accountId);
    if (account == null) {
      return null;
    }
    return new Transaction(accountId, new Money(account.currency, 0L), parentId);
  }

  public static void delete(long id, boolean markAsVoid) {
    Uri.Builder builder = ContentUris.appendId(CONTENT_URI.buildUpon(), id);
    if (markAsVoid) {
      builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_MARK_VOID, "1");
    }
    cr().delete(builder.build(), null, null);
  }

  public static void undelete(long id) {
    Uri uri = ContentUris.appendId(CONTENT_URI.buildUpon(), id)
        .appendPath(TransactionProvider.URI_SEGMENT_UNDELETE).build();
    cr().update(uri, null, null, null);
  }

  protected Transaction() {
    setDate(new Date());
    this.crStatus = CrStatus.UNRECONCILED;
  }

  public Transaction(long accountId, Money amount) {
    this();
    this.setAccountId(accountId);
    this.setAmount(amount);
  }

  public Transaction(long accountId, Money amount, Long parentId) {
    this(accountId, amount);
    setParentId(parentId);
  }

  public Long getCatId() {
    return catId;
  }

  public void setCatId(Long catId) {
    this.catId = catId;
  }

  public void setDate(Date date) {
    if (date == null) {
      throw new RuntimeException("Transaction date cannot be set to null");
    }
    this.date = date;
  }

  private void setDate(Long unixEpoch) {
    this.setDate(new Date(unixEpoch));
  }

  public Date getDate() {
    return date;
  }

  /**
   * updates the payee string to a new value
   * it will me mapped to an existing or new row in payee table during save
   *
   * @param payee
   */
  public void setPayee(String payee) {
    if (!this.payee.equals(payee)) {
      this.setPayeeId(null);
    }
    this.payee = payee;
  }

  /**
   * updates the payee to a row that already exists in the DB
   *
   * @param payee
   * @param payeeId
   */
  public void updatePayeeWithId(String payee, Long payeeId) {
    this.setPayee(payee);
    this.setPayeeId(payeeId);
  }

  @Override
  public Uri save() {
    Uri uri;
    try {
      ContentProviderResult[] result = cr().applyBatch(TransactionProvider.AUTHORITY,
          buildSaveOperations(0, -1, false));
      if (getId() == 0) {
        //we need to find a uri, otherwise we would crash. Need to handle?
        uri = result[0].uri;
        updateFromResult(result);
      } else {
        uri = Uri.parse(CONTENT_URI + "/" + getId());
      }
    } catch (RemoteException | OperationApplicationException e) {
      return null;
    }

    if (pictureUri != null) {
      ContribFeature.ATTACH_PICTURE.recordUsage();
    }

    if (initialPlan != null) {
      originTemplate = new Template(this, initialPlan.title);
      originTemplate.setPlanExecutionAutomatic(true);
      originTemplate.setPlan(initialPlan);
      originTemplate.save(getId());
    }
    return uri;
  }

  protected void updateFromResult(ContentProviderResult[] result) {
    setId(ContentUris.parseId(result[0].uri));
  }

  void addCommitOperations(Uri uri, ArrayList<ContentProviderOperation> ops) {
    if (isSplit()) {
      String idStr = String.valueOf(getId());
      ContentValues statusValues = new ContentValues();
      String statusUncommited = String.valueOf(STATUS_UNCOMMITTED);
      String[] uncommitedPartOrPeerSelectArgs = getPartOrPeerSelectArgs(statusUncommited);
      ops.add(ContentProviderOperation.newDelete(uri).withSelection(
          getPartOrPeerSelect() + "  AND " + KEY_STATUS + " != ?", uncommitedPartOrPeerSelectArgs).build());
      statusValues.put(KEY_STATUS, STATUS_NONE);
      //for a new split, both the parent and the parts are in state uncommitted
      //when we edit a split only the parts are in state uncommitted,
      //in any case we only update the state for rows that are uncommitted, to
      //prevent altering the state of a parent (e.g. from exported to non-exported)
      ops.add(ContentProviderOperation.newUpdate(uri).withValues(statusValues).withSelection(
          KEY_STATUS + " = ? AND " + KEY_ROWID + " = ?",
          new String[]{statusUncommited, idStr}).build());
      ops.add(ContentProviderOperation.newUpdate(uri).withValues(statusValues).withSelection(
          getPartOrPeerSelect() + "  AND " + KEY_STATUS + " = ?",
          uncommitedPartOrPeerSelectArgs).build());
    }
  }

  protected String getPartOrPeerSelect() {
    return null;
  }

  private String[] getPartOrPeerSelectArgs(String extra) {
    int count =  StringUtils.countMatches(getPartOrPeerSelect(), '?');
    List<String> args = new ArrayList<>();
    args.addAll(Collections.nCopies(count, String.valueOf(getId())));
    if (extra != null) {
      args.add(extra);
    }
    return args.toArray(new String[args.size()]);
  }

  /**
   * all Split Parts are cloned and we work with the uncommitted clones
   *
   * @param clone if true an uncommited clone of the instance is prepared
   */

  public void prepareForEdit(boolean clone) {
    if (isSplit()) {
      Long oldId = getId();
      if (clone) {
        status = STATUS_UNCOMMITTED;
        setDate(new Date());
        saveAsNew();
      }
      String idStr = String.valueOf(oldId);
      //we only create uncommited clones if none exist yet
      Cursor c = cr().query(getContentUri(), new String[]{KEY_ROWID},
          KEY_PARENTID + " = ? AND NOT EXISTS (SELECT 1 from " + getUncommittedView()
              + " WHERE " + KEY_PARENTID + " = ?)", new String[]{idStr, idStr}, null);
      if (c != null) {
        c.moveToFirst();
        while (!c.isAfterLast()) {
          Transaction part = getSplitPart(c.getLong(0));
          if (part != null) {
            part.status = STATUS_UNCOMMITTED;
            part.setParentId(getId());
            part.saveAsNew();
          }
          c.moveToNext();
        }
        c.close();
      }
      inEditState = true;
    }
  }

  protected Transaction getSplitPart(long parentId) {
    return Transaction.getInstanceFromDb(parentId);
  }

  public Uri getContentUri() {
    return CONTENT_URI;
  }

  public String getUncommittedView() {
    return VIEW_UNCOMMITTED;
  }

  /**
   * Constructs the {@link ArrayList} of {@link ContentProviderOperation}s necessary for saving
   * the transaction
   * as a side effect calls {@link Payee#require(String)}
   *
   * @param offset       Number of operations that are already added to the batch, needed for calculating back references
   * @param parentOffset if not -1, it indicates at which position in the batch the parent of a new split transaction is situated.
   *                     Is used from SyncAdapter for creating split transactions
   * @param callerIsSyncAdapter
   * @return the URI of the transaction. Upon creation it is returned from the content provider
   */
  public ArrayList<ContentProviderOperation> buildSaveOperations(int offset, int parentOffset, boolean callerIsSyncAdapter) {
    Uri uri = getUriForSave(callerIsSyncAdapter);
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ContentValues initialValues = buildInitialValues();
    if (getId() == 0) {
      ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(uri).withValues(initialValues);
      if (parentOffset != -1) {
        builder.withValueBackReference(KEY_PARENTID, parentOffset);
      }
      ops.add(builder.build());
      addOriginPlanInstance(ops);
    } else {
      ops.add(ContentProviderOperation
          .newUpdate(uri.buildUpon().appendPath(String.valueOf(getId())).build())
          .withValues(initialValues).build());
    }
    return ops;
  }

  protected Uri getUriForSave(boolean callerIsSyncAdapter) {
    return callerIsSyncAdapter ? CALLER_IS_SYNC_ADAPTER_URI : CONTENT_URI;
  }

  protected void addOriginPlanInstance(ArrayList<ContentProviderOperation> ops) {
    if (originPlanInstanceId != null) {
      ContentValues values = new ContentValues();
      values.put(KEY_TEMPLATEID, originTemplate.getId());
      values.put(KEY_INSTANCEID, originPlanInstanceId);
      ops.add(ContentProviderOperation.newInsert(TransactionProvider.PLAN_INSTANCE_STATUS_URI)
          .withValues(values).withValueBackReference(KEY_TRANSACTIONID, 0).build());
    }
  }

  ContentValues buildInitialValues() {
    ContentValues initialValues = new ContentValues();

    Long payeeStore;
    if (getPayeeId() != null) {
      payeeStore = getPayeeId();
    } else {
      payeeStore =
          (getPayee() != null && !getPayee().equals("")) ?
              Payee.require(getPayee()) :
              null;
    }
    initialValues.put(KEY_COMMENT, getComment());
    initialValues.put(KEY_REFERENCE_NUMBER, getReferenceNumber());
    //store in UTC
    initialValues.put(KEY_DATE, getDate().getTime() / 1000);

    initialValues.put(KEY_AMOUNT, getAmount().getAmountMinor());
    initialValues.put(KEY_CATID, getCatId());
    initialValues.put(KEY_PAYEEID, payeeStore);
    initialValues.put(KEY_METHODID, getMethodId());
    initialValues.put(KEY_CR_STATUS, crStatus.name());
    initialValues.put(KEY_ACCOUNTID, getAccountId());
    initialValues.put(KEY_UUID, requireUuid());

    savePicture(initialValues);
    if (getId() == 0) {
      initialValues.put(KEY_PARENTID, getParentId());
      initialValues.put(KEY_STATUS, status);
    }
    return initialValues;
  }

  private void throwExternalNotAvailable() {
    throw new ExternalStorageNotAvailableException();
  }

  protected void savePicture(ContentValues initialValues) {
    if (pictureUri != null) {
      String pictureUriBase = PictureDirHelper.getPictureUriBase(false);
      if (pictureUriBase == null) {
        throwExternalNotAvailable();
      }
      if (pictureUri.toString().startsWith(pictureUriBase)) {
        Timber.d("got Uri in our home space, nothing todo");
      } else {
        pictureUriBase = PictureDirHelper.getPictureUriBase(true);
        if (pictureUriBase == null) {
          throwExternalNotAvailable();
        }
        boolean isInTempFolder = pictureUri.toString().startsWith(pictureUriBase);
        Uri homeUri = PictureDirHelper.getOutputMediaUri(false);
        if (homeUri == null) {
          throwExternalNotAvailable();
        }
        try {
          if (isInTempFolder && homeUri.getScheme().equals("file")) {
            if (new File(pictureUri.getPath()).renameTo(new File(homeUri.getPath()))) {
              setPictureUri(homeUri);
            } else {
              //fallback
              copyPictureHelper(true, homeUri);
            }
          } else {
            copyPictureHelper(isInTempFolder, homeUri);
          }
        } catch (IOException e) {
          throw new UnknownPictureSaveException(pictureUri, homeUri, e);
        }
      }
      initialValues.put(KEY_PICTURE_URI, pictureUri.toString());
    } else {
      initialValues.putNull(KEY_PICTURE_URI);
    }
  }

  private void copyPictureHelper(boolean delete, Uri homeUri) throws IOException {
    FileCopyUtils.copy(pictureUri, homeUri);
    if (delete) {
      new File(pictureUri.getPath()).delete();
    }
    setPictureUri(homeUri);
  }

  public Uri saveAsNew() {
    Long oldId = getId();
    setId(0L);
    uuid = null;
    Uri result = save();
    if (isSplit()) {
      Cursor c = cr().query(getContentUri(), new String[]{KEY_ROWID},
          KEY_PARENTID + " = ?", new String[]{String.valueOf(oldId)}, null);
      if (c != null) {
        c.moveToFirst();
        while (!c.isAfterLast()) {
          Transaction part = getSplitPart(c.getLong(c.getColumnIndex(KEY_ROWID)));
          if (part != null) {
            part.setParentId(getId());
            part.saveAsNew();
          }
          c.moveToNext();
        }
        c.close();
      }
    }
    return result;
  }

  /**
   * @param whichTransactionId
   * @param whereAccountId
   */
  public static void move(long whichTransactionId, long whereAccountId) {
    ContentValues args = new ContentValues();
    args.put(KEY_ACCOUNTID, whereAccountId);
    cr().update(Uri.parse(
        CONTENT_URI + "/" + whichTransactionId + "/" + TransactionProvider.URI_SEGMENT_MOVE + "/" + whereAccountId),
        null, null, null);
  }

  public static int count(Uri uri, String selection, String[] selectionArgs) {
    Cursor cursor = cr().query(uri, new String[]{"count(*)"},
        selection, selectionArgs, null);
    if (cursor.getCount() == 0) {
      cursor.close();
      return 0;
    } else {
      cursor.moveToFirst();
      int result = cursor.getInt(0);
      cursor.close();
      return result;
    }
  }

  public static int countAll(Uri uri) {
    return count(uri, null, null);
  }

  public static int countPerCategory(Uri uri, long catId) {
    return count(uri, KEY_CATID + " = ?", new String[]{String.valueOf(catId)});
  }

  public static int countPerMethod(Uri uri, long methodId) {
    return count(uri, KEY_METHODID + " = ?", new String[]{String.valueOf(methodId)});
  }

  public static int countPerAccount(Uri uri, long accountId) {
    return count(uri, KEY_ACCOUNTID + " = ?", new String[]{String.valueOf(accountId)});
  }

  public static int countPerCategory(long catId) {
    return countPerCategory(CONTENT_URI, catId);
  }

  public static int countPerMethod(long methodId) {
    return countPerMethod(CONTENT_URI, methodId);
  }

  public static int countPerAccount(long accountId) {
    return countPerAccount(CONTENT_URI, accountId);
  }

  public static int countPerUuid(String uuid) {
    return countPerUuid(CONTENT_URI, uuid);
  }

  private static int countPerUuid(Uri contentUri, String uuid) {
    return count(contentUri, KEY_UUID + " = ?", new String[]{uuid});
  }

  public static int countAll() {
    return countAll(CONTENT_URI);
  }

  /**
   * @return the number of transactions that have been created since creation of the db based on sqllite sequence
   */
  public static Long getSequenceCount() {
    Cursor mCursor = cr().query(TransactionProvider.SQLITE_SEQUENCE_TRANSACTIONS_URI,
        null, null, null, null);
    if (mCursor == null) {
      return 0L;
    }
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return 0L;
    }
    mCursor.moveToFirst();
    Long result = mCursor.getLong(0);
    mCursor.close();
    return result;
  }

  public String compileDescription(Context ctx, CurrencyFormatter currencyFormatter) {
    StringBuilder sb = new StringBuilder();
    sb.append(ctx.getString(R.string.amount));
    sb.append(" : ");
    sb.append(currencyFormatter.formatCurrency(getAmount()));
    sb.append("\n");
    if (getCatId() != null && getCatId() > 0) {
      sb.append(ctx.getString(R.string.category));
      sb.append(" : ");
      sb.append(getLabel());
      sb.append("\n");
    }
    if (isTransfer()) {
      sb.append(ctx.getString(R.string.account));
      sb.append(" : ");
      sb.append(getLabel());
      sb.append("\n");
    }
    //comment
    if (!getComment().equals("")) {
      sb.append(ctx.getString(R.string.comment));
      sb.append(" : ");
      sb.append(getComment());
      sb.append("\n");
    }
    //payee
    if (!getPayee().equals("")) {
      sb.append(ctx.getString(
          getAmount().getAmountMajor().signum() == 1 ? R.string.payer : R.string.payee));
      sb.append(" : ");
      sb.append(getPayee());
      sb.append("\n");
    }
    //Method
    if (getMethodId() != null) {
      sb.append(ctx.getString(R.string.method));
      sb.append(" : ");
      sb.append(getMethodLabel());
      sb.append("\n");
    }
    sb.append("UUID : ");
    sb.append(requireUuid());
    return sb.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Transaction other = (Transaction) obj;
    if (getAccountId() == null) {
      if (other.getAccountId() != null)
        return false;
    } else if (!getAccountId().equals(other.getAccountId()))
      return false;
    if (getAmount() == null) {
      if (other.getAmount() != null)
        return false;
    } else if (!getAmount().equals(other.getAmount()))
      return false;
    if (getCatId() == null) {
      if (other.getCatId() != null)
        return false;
    } else if (!getCatId().equals(other.getCatId()))
      return false;
    if (getComment() == null) {
      if (other.getComment() != null)
        return false;
    } else if (!getComment().equals(other.getComment()))
      return false;
    if (getDate() == null) {
      if (other.getDate() != null)
        return false;
    } else if (Math.abs(getDate().getTime() - other.getDate().getTime()) > 30000) //30 seconds tolerance
      return false;
    if (getId() == null) {
      if (other.getId() != null)
        return false;
    } else if (!getId().equals(other.getId()))
      return false;
    //label is constructed on hoc by database as a consquence of transfer_account and category
    //and is not yet set when transaction is not saved, hence we do not consider it relevant
    //here for equality
/*    if (label == null) {
      if (other.label != null)
        return false;
    } else if (!label.equals(other.label))
      return false;*/
    if (getMethodId() == null) {
      if (other.getMethodId() != null)
        return false;
    } else if (!getMethodId().equals(other.getMethodId()))
      return false;
    if (getPayee() == null) {
      if (other.getPayee() != null)
        return false;
    } else if (!getPayee().equals(other.getPayee()))
      return false;
    if (pictureUri == null) {
      if (other.pictureUri != null)
        return false;
    } else if (!pictureUri.equals(other.pictureUri))
      return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = this.getComment() != null ? this.getComment().hashCode() : 0;
    result = 31 * result + (this.getPayee() != null ? this.getPayee().hashCode() : 0);
    result = 31 * result + (this.getReferenceNumber() != null ? this.getReferenceNumber().hashCode() : 0);
    result = 31 * result + (this.getLabel() != null ? this.getLabel().hashCode() : 0);
    result = 31 * result + (this.getDate() != null ? this.getDate().hashCode() : 0);
    result = 31 * result + (this.getAmount() != null ? this.getAmount().hashCode() : 0);
    result = 31 * result + (this.getTransferAmount() != null ? this.getTransferAmount().hashCode() : 0);
    result = 31 * result + (this.catId != null ? this.catId.hashCode() : 0);
    result = 31 * result + (this.getAccountId() != null ? this.getAccountId().hashCode() : 0);
    result = 31 * result + (this.getMethodId() != null ? this.getMethodId().hashCode() : 0);
    result = 31 * result + (this.getMethodLabel() != null ? this.getMethodLabel().hashCode() : 0);
    result = 31 * result + (this.getParentId() != null ? this.getParentId().hashCode() : 0);
    result = 31 * result + (this.getPayeeId() != null ? this.getPayeeId().hashCode() : 0);
    result = 31 * result + (this.originTemplate != null ? this.originTemplate.hashCode() : 0);
    result = 31 * result + (this.originPlanInstanceId != null ? this.originPlanInstanceId.hashCode() : 0);
    result = 31 * result + this.status;
    result = 31 * result + (this.crStatus != null ? this.crStatus.hashCode() : 0);
    result = 31 * result + (this.pictureUri != null ? this.pictureUri.hashCode() : 0);
    return result;
  }

  public Uri getPictureUri() {
    return pictureUri;
  }

  public void setPictureUri(Uri pictureUriIn) {
    this.pictureUri = pictureUriIn;
  }

  public static class ExternalStorageNotAvailableException extends IllegalStateException {
  }

  public static class UnknownPictureSaveException extends IllegalStateException {
    public Uri pictureUri, homeUri;

    public UnknownPictureSaveException(Uri pictureUri, Uri homeUri, IOException e) {
      super(e);
      this.pictureUri = pictureUri;
      this.homeUri = homeUri;
    }
  }

  public static long findByUuid(String uuid) {
    String selection = KEY_UUID + " = ?";
    String[] selectionArgs = new String[]{uuid};

    Cursor mCursor = cr().query(CONTENT_URI,
        new String[]{KEY_ROWID}, selection, selectionArgs, null);
    if (mCursor.getCount() == 0) {
      mCursor.close();
      return -1;
    } else {
      mCursor.moveToFirst();
      long result = mCursor.getLong(0);
      mCursor.close();
      return result;
    }
  }

  public void cleanupCanceledEdit() {
    if (isSplit()) {
      String idStr = String.valueOf(getId());
      String statusUncommited = String.valueOf(STATUS_UNCOMMITTED);
      cr().delete(getContentUri(),getPartOrPeerSelect() + "  AND " + KEY_STATUS + " = ?",
          getPartOrPeerSelectArgs(statusUncommited));
      cr().delete(getContentUri(), KEY_STATUS + " = ? AND " + KEY_ROWID + " = ?",
          new String[]{statusUncommited, idStr});
    }
  }

  public boolean isTransfer() {
    return operationType() == TYPE_TRANSFER;
  }

  public boolean isSplit() {
    return operationType() == TYPE_SPLIT;
  }

  public boolean isSplitpart() {
    return !(parentId == null || parentId == 0);
  }

  public int operationType() {
    return TYPE_TRANSACTION;
  }
}
