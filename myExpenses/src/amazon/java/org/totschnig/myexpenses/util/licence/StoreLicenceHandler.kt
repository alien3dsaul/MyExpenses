package org.totschnig.myexpenses.util.licence

import android.app.Activity
import android.content.Context
import com.amazon.device.iap.model.Product
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.Receipt
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ContribInfoDialogActivity
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.contrib.Config.amazonSkus
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class StoreLicenceHandler(context: MyApplication, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler, prefHandler: PrefHandler) :
        PlayStoreLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler) {

    override fun initBillingManager(activity: Activity, query: Boolean): BillingManager {
        val billingUpdatesListener = object : AmazonBillingUpdatesListener {
            override fun onPurchase(receipt: Receipt): Boolean {
                handlePurchaseForLicence(receipt.sku, receipt.receiptId)
                (activity as? BillingListener)?.onLicenceStatusSet(prettyPrintStatus(activity))
                return licenceStatus != null
            }

            override fun onPurchasesUpdated(purchases: MutableList<Receipt>) {
                val oldStatus = licenceStatus
                registerInventory(purchases)
                if (oldStatus != licenceStatus) {
                    (activity as? BillingListener)?.onLicenceStatusSet(prettyPrintStatus(activity))
                }
            }

            override fun onProductDataResponse(productData: MutableMap<String, Product>) {
                storeSkuDetails(productData)
            }

            override fun onPurchaseFailed(resultCode: PurchaseResponse.RequestStatus) {
                log().w("onPurchaseFailed() resultCode: %s", resultCode)
                (activity as? ContribInfoDialogActivity)?.onPurchaseFailed(resultCode.ordinal)
            }
        }
        return BillingManagerAmazon(activity, billingUpdatesListener, query)
    }

    private fun registerInventory(purchases: MutableList<Receipt>) {
        val receipt = findHighestValidPurchase(purchases)
        receipt?.let {
            handlePurchaseForLicence(it.sku, it.receiptId)
        } ?: kotlin.run { maybeCancel() }
    }

    private fun findHighestValidPurchase(purchases: List<Receipt>) =
            purchases.filter { !it.isCanceled && extractLicenceStatusFromSku(it.sku) != null }
                    .maxByOrNull { extractLicenceStatusFromSku(it.sku)?.ordinal ?: 0 }

    private fun storeSkuDetails(productData: MutableMap<String, Product>) {
        val editor = pricesPrefs.edit()
        amazonSkus.forEach { sku ->
            val product = productData[sku]
            product?.let {
                log().d("Sku: %s", it.toString())
                editor.putString(sku, it.price)
            } ?: kotlin.run {
                log().d("Did not find details for %s", sku)
            }
        }
        editor.apply()
    }

    override fun launchPurchase(aPackage: Package, shouldReplaceExisting: Boolean, billingManager: BillingManager) {
        (billingManager as? BillingManagerAmazon)?.initiatePurchaseFlow(getSkuForPackage(aPackage))
    }

    override val proPackages: Array<ProfessionalPackage>
        get() = arrayOf(ProfessionalPackage.Amazon)

    override val professionalPriceShortInfo: String
        get() {
            var priceInfo = joinPriceInformation(ProfessionalPackage.Professional_1, ProfessionalPackage.Professional_12)
            if (licenceStatus === LicenceStatus.EXTENDED) {
                val regularPrice = pricesPrefs.getString(Config.SKU_PROFESSIONAL_12, null)
                if (regularPrice != null) {
                    priceInfo += ". " + context.getString(R.string.extended_upgrade_goodie_subscription_amazon, 15, regularPrice)
                }
            }
            return priceInfo
        }

    override fun getDisplayPriceForPackage(aPackage: Package) = pricesPrefs.getString(getSkuForPackage(aPackage), null)

    override val proPackagesForExtendOrSwitch: Array<ProfessionalPackage>?
        get() = null

    override fun getProLicenceAction(context: Context) = ""

    override fun supportSingleFeaturePurchase(feature: ContribFeature) = false
}