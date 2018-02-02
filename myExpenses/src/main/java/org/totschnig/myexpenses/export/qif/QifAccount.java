//Copyright (c) 2010 Denis Solonenko (Financisto)
//made available under the terms of the GNU Public License v2.0
//adapted to My Expenses by Michael Totschnig

package org.totschnig.myexpenses.export.qif;

import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.AccountType;
import org.totschnig.myexpenses.model.Money;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.totschnig.myexpenses.export.qif.QifUtils.trimFirstChar;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 2/7/11 8:03 PM
 */
public class QifAccount {

    public String type = "";
    public String memo = "";
    public String desc = "";
    public BigDecimal openinBalance = null;

    public Account dbAccount;
    public final List<QifTransaction> transactions = new ArrayList<>();

    public static QifAccount fromAccount(Account account) {
        QifAccount qifAccount = new QifAccount();
        qifAccount.type = account.getType().toQifName();
        qifAccount.memo = account.getLabel();
        qifAccount.desc = account.description;
        return qifAccount;
    }

    public Account toAccount(Currency currency) {
        Account a = new Account();
        a.currency = currency;
        a.setLabel(memo);
        a.setType(AccountType.fromQifName(type));
        a.description = desc;
        if (openinBalance != null) {
            a.openingBalance = new Money(currency,openinBalance);

        }
        return a;
    }

    public void writeTo(QifBufferedWriter w) throws IOException {
        w.writeAccountsHeader();
        w.write("N").write(memo).newLine();
        w.write("T").write(type).newLine();
        w.end();
    }

    public void readFrom(QifBufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("^")) {
                break;
            }
            if (line.startsWith("N")) {
                this.memo = trimFirstChar(line);
            } else if (line.startsWith("T")) {
                this.type = trimFirstChar(line);
            } else if (line.startsWith("D")) {
              this.desc = trimFirstChar(line);
            }
        }
    }
}
