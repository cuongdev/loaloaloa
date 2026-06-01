package com.tingting.notifier.parser

/**
 * Package-ID → display-name map for supported Vietnamese banks / e-wallets,
 * recovered from the original TingTing app. Used to (a) whitelist which app
 * notifications to process and (b) resolve the spoken/displayed bank name.
 */
object BankRegistry {

    private val banks: Map<String, String> = mapOf(
        "com.vnpay.abbank" to "ABBank",
        "mobile.acb.com.vn" to "ACB",
        "com.acb.acbb.prod" to "ACB ONE BIZ",
        "com.vnpay.Agribank3g" to "Agribank",
        "com.bab.retailUAT" to "Bac A Bank",
        "vn.banvietbank.mobilebanking" to "BVBank",
        "com.vnpay.bvbank" to "BaoViet Bank",
        "com.vnpay.bidc" to "BIDC",
        "com.vnpay.bidv" to "BIDV",
        "xyz.be.cake" to "Cake",
        "cbbank.vn.mobile" to "CBBank",
        "com.citibank.mobile.vn" to "Citibank",
        "com.vnpay.coopbank" to "Co-opBank",
        "com.vn.dongabank" to "DongA Bank",
        "com.vnpay.EximBankOmni" to "Eximbank",
        "com.vnpay.hdbank" to "HDBank",
        "vn.hsbc.hsbcvietnam" to "HSBC",
        "com.vnpay.ivb" to "IVB",
        "com.kasikornbank.kplus.vn" to "Kasikornbank",
        "com.sunshine.ksbank" to "KienLongBank",
        "com.ocb.liobank" to "Liobank",
        "vn.com.lpb.lienviet24h" to "LPBank",
        "com.mbmobile" to "MB Bank",
        "com.mservice.momotransfer" to "MoMo",
        "vn.com.msb.smartBanking" to "MSB",
        "ops.namabank.com.vn" to "Nam A Bank",
        "com.ncb.bank" to "NCB",
        "vn.com.ocb.awe" to "OCB",
        "com.aci.ocean.mobile" to "OceanBank",
        "pgbankApp.pgbank.com.vn" to "PGBank",
        "com.vnpay.publicbank" to "Public Bank",
        "com.pvcombank.retail" to "PVcomBank",
        "com.sacombank.ewallet" to "Sacombank",
        "com.vnpay.SCB" to "SCB",
        "vn.com.seabank.mb1" to "SeABank",
        "vn.shb.mbanking" to "SHB",
        "vn.shb.saha.mbanking" to "SHB SAHA",
        "com.sc.mobilebanking.vn" to "Standard Chartered",
        "vn.com.techcombank.bb.app" to "Techcombank",
        "io.lifestyle.plus" to "Timo",
        "com.tpb.mb.gprsandroid" to "TPBank",
        "com.vnpay.NHCSXH" to "VBSP",
        "com.vib.myvib2" to "VIB",
        "phn.com.vn.mb" to "VietABank",
        "com.vnpay.vietbank" to "VietBank",
        "com.VCB" to "Vietcombank",
        "com.vietinbank.ipay" to "VietinBank",
        "com.bplus.vtpay" to "Viettel Money",
        "com.vietqr.product" to "VietQR",
        "com.finx.vikki" to "Vikki",
        "vnpay.smartacccount" to "VNPay",
        "com.vnpay.vpbankonline" to "VPBank",
        "vn.com.woori.smart" to "Woori",
        "vn.com.vng.zalopay" to "ZaloPay",
    )

    /** Display name for a package id, or null if it is not a supported bank/wallet. */
    fun nameFor(packageName: String): String? = banks[packageName]

    /** True when [packageName] is a supported bank/wallet (and its notifications should be processed). */
    fun isBank(packageName: String): Boolean = banks.containsKey(packageName)

    /** All supported package ids. */
    fun supportedPackages(): Set<String> = banks.keys
}
