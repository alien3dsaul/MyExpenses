package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.ContentProviderOperation
import android.content.ContentProviderOperation.newDelete
import android.content.ContentProviderOperation.newUpdate
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import app.cash.copper.Query
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.createParty
import org.totschnig.myexpenses.db2.saveParty
import org.totschnig.myexpenses.provider.BaseTransactionProvider.Companion.ACCOUNTS_MINIMAL_URI_WITH_AGGREGATES
import org.totschnig.myexpenses.provider.DatabaseConstants.*
import org.totschnig.myexpenses.provider.TransactionProvider.*
import org.totschnig.myexpenses.provider.appendBooleanQueryParameter
import org.totschnig.myexpenses.provider.filter.KEY_FILTER
import org.totschnig.myexpenses.provider.filter.PayeeCriterion
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.replace
import org.totschnig.myexpenses.viewmodel.data.Debt
import org.totschnig.myexpenses.viewmodel.data.Party
import timber.log.Timber
import java.util.*

const val KEY_EXPANDED_ITEM = "expandedItem"

class PartyListViewModel(
    application: Application,
    val savedStateHandle: SavedStateHandle
) : ContentResolvingAndroidViewModel(application) {

    private lateinit var debts: Map<Long, List<Debt>>

    var filter: String?
        get() = savedStateHandle.get<String>(KEY_FILTER)
        set(value) {
            savedStateHandle[KEY_FILTER] = value
        }

    fun setExpandedItem(id: Long) {
        savedStateHandle[KEY_EXPANDED_ITEM] = id
    }

    fun getDebts(partyId: Long): List<Debt>? = if (::debts.isInitialized) debts[partyId] else null

    @OptIn(ExperimentalCoroutinesApi::class)
    val parties: Flow<List<Party>> = savedStateHandle.getLiveData(KEY_FILTER, "")
        .asFlow()
        .distinctUntilChanged()
        .flatMapLatest {
            val (selection, selectionArgs) = joinQueryAndAccountFilter(
                it,
                savedStateHandle.get<Long>(KEY_ACCOUNTID),
                KEY_PAYEE_NAME_NORMALIZED, KEY_PAYEEID, TABLE_PAYEES
            )
            contentResolver.observeQuery(
                PAYEES_URI.buildUpon()
                    .appendBooleanQueryParameter(QUERY_PARAMETER_HIERARCHICAL)
                    .build(), null,
                buildString {
                    append(KEY_PARENTID).append(" IS NULL")
                    if (!selection.isNullOrEmpty()) append(" AND ").append(selection)
                },
                selectionArgs, null, true
            ).transform { query ->
                val list = withContext(Dispatchers.IO) {
                    query.run()?.use { cursor ->
                        val items = mutableListOf<Party>()
                        val duplicates = mutableListOf<Party>()
                        var currentItem: Party? = null
                        while (cursor.moveToNext()) {
                            val element = Party.fromCursor(cursor)
                            if (cursor.getLongOrNull(KEY_PARENTID) == null) {
                                if (currentItem != null) {
                                    items.add(currentItem.copy(
                                        duplicates = buildList { addAll(duplicates) }
                                    ))
                                    duplicates.clear()
                                }
                                currentItem = element
                            } else {
                                duplicates.add(element)
                            }
                        }
                        if (currentItem != null) {
                            items.add(currentItem.copy(duplicates = buildList { addAll(duplicates) }))
                        }
                        items
                    }
                }
                if (list != null) {
                    emit(list)
                }
            }
        }.zip(
            savedStateHandle.getLiveData<Long?>(KEY_EXPANDED_ITEM, null).asFlow()
        ) { parties, expandedItem ->
            if (expandedItem == null) parties else parties.flatMap {
                buildList {
                    add(it)
                    if (it.id == expandedItem) addAll(it.duplicates)
                }
            }
        }

    fun loadDebts(): LiveData<Unit> = liveData(context = coroutineContext()) {
        contentResolver.observeQuery(DEBTS_URI, notifyForDescendants = true)
            .mapToList {
                Debt.fromCursor(it, currencyContext)
            }.collect { list ->
                this@PartyListViewModel.debts = list.groupBy { it.payeeId }
                emit(Unit)
            }
    }

    fun deleteParty(id: Long): LiveData<Result<Int>> = liveData(context = coroutineContext()) {
        try {
            emit(
                Result.success(
                    contentResolver.delete(
                        ContentUris.withAppendedId(
                            PAYEES_URI,
                            id
                        ), null, null
                    )
                )
            )
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private fun updatePartyFilters(old: Set<Long>, new: Long) {
        contentResolver.query(ACCOUNTS_MINIMAL_URI_WITH_AGGREGATES, null, null, null, null)
            ?.use { cursor ->
                updateFilterHelper(old, new, cursor, MyExpensesViewModel::prefNameForCriteria)
            }
    }


    private fun updatePartyBudgets(old: Set<Long>, new: Long) {
        contentResolver.query(
            BUDGETS_URI,
            arrayOf("$TABLE_BUDGETS.$KEY_ROWID"),
            null,
            null,
            null
        )?.use { cursor ->
            updateFilterHelper(old, new, cursor, BudgetViewModel::prefNameForCriteria)
        }
    }

    private fun updateFilterHelper(
        old: Set<Long>,
        new: Long,
        cursor: Cursor,
        prefNameCreator: (Long) -> String
    ) {
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val payeeFilterKey =
                prefNameCreator(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ROWID))).format(
                    Locale.ROOT,
                    KEY_PAYEEID
                )
            val oldPayeeFilterValue = prefHandler.getString(payeeFilterKey, null)
            val oldCriteria: PayeeCriterion? = oldPayeeFilterValue?.let {
                PayeeCriterion.fromStringExtra(it)
            }
            if (oldCriteria != null) {
                val oldSet = oldCriteria.values.toSet()
                val newSet: Set<Long> = oldSet.replace(old, new)
                if (oldSet != newSet) {
                    val labelList = mutableListOf<String>()
                    contentResolver.query(
                        PAYEES_URI, arrayOf(KEY_PAYEE_NAME),
                        "$KEY_ROWID IN (${newSet.joinToString()})", null, null
                    )?.use {
                        it.moveToFirst()
                        while (!it.isAfterLast) {
                            labelList.add(it.getString(0))
                            it.moveToNext()
                        }
                    }
                    val newPayeeFilterValue = PayeeCriterion(
                        labelList.joinToString(","),
                        *newSet.toLongArray()
                    ).toString()
                    Timber.d(
                        "Updating %s (%s -> %s",
                        payeeFilterKey,
                        oldPayeeFilterValue,
                        newPayeeFilterValue
                    )
                    prefHandler.putString(payeeFilterKey, newPayeeFilterValue)
                }
            }
            cursor.moveToNext()
        }
    }

    fun mergeParties(itemIds: LongArray, keepId: Long) {
        viewModelScope.launch(context = coroutineContext()) {
            check(itemIds.contains(keepId))

            val contentValues = ContentValues(1).apply {
                put(KEY_PAYEEID, keepId)
            }
            itemIds.subtract(setOf(keepId)).let {
                val inOp = "IN (${it.joinToString()})"
                val where = "$KEY_PAYEEID $inOp"
                val operations = ArrayList<ContentProviderOperation>().apply {
                    add(
                        newUpdate(ACCOUNTS_URI).withValue(KEY_SEALED, -1)
                            .withSelection("$KEY_SEALED = 1", null).build()
                    )
                    add(
                        newUpdate(DEBTS_URI).withValue(KEY_SEALED, -1)
                            .withSelection("$KEY_SEALED = 1", null).build()
                    )
                    add(
                        newUpdate(DEBTS_URI).withValues(contentValues)
                            .withSelection(where, null).build()
                    )
                    add(
                        newUpdate(TRANSACTIONS_URI).withValues(contentValues)
                            .withSelection(where, null).build()
                    )
                    add(
                        newUpdate(TEMPLATES_URI).withValues(contentValues)
                            .withSelection(where, null).build()
                    )
                    add(
                        newUpdate(CHANGES_URI).withValues(contentValues)
                            .withSelection(where, null).build()
                    )
                    add(
                        newDelete(PAYEES_URI).withSelection(
                            "$KEY_ROWID $inOp",
                            null
                        ).build()
                    )
                    add(
                        newUpdate(ACCOUNTS_URI).withValue(KEY_SEALED, 1)
                            .withSelection("$KEY_SEALED = -1", null).build()
                    )
                    add(
                        newUpdate(DEBTS_URI).withValue(KEY_SEALED, 1)
                            .withSelection("$KEY_SEALED = -1", null).build()
                    )
                }
                val size =
                    contentResolver.applyBatch(AUTHORITY, operations).size
                if (size == operations.size) {
                    updatePartyFilters(it, keepId)
                    updatePartyBudgets(it, keepId)
                } else {
                    CrashHandler.report(Exception("Unexpected result while merging Parties, result size is : $size"))
                }
            }
        }
    }

    fun saveParty(id: Long, name: String, shortName: String?): LiveData<Boolean> =
        liveData(context = coroutineContext()) {
            val party = org.totschnig.myexpenses.model2.Party.create(
                id = id,
                name = name,
                shortName = shortName
            )
            emit(
                try {
                    if (id == 0L) repository.createParty(party) else repository.saveParty(party)
                    true
                } catch (e: SQLiteConstraintException) {
                    false
                }
            )
        }
}