package com.factory.timetilescountdownwidgets.billing

import android.util.Base64
import com.android.billingclient.api.Purchase
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Local signature verification against the RSA public key from
 * Play Console > Monetization setup. This is a defense-in-depth check only —
 * production apps should also verify purchase tokens server-side via the
 * Play Developer API before granting entitlements.
 */
object PurchaseVerifier {

    // TODO: paste the Base64-encoded RSA public key from Play Console > Monetization setup.
    private const val BASE64_PUBLIC_KEY = ""

    fun isValid(purchase: Purchase): Boolean {
        if (BASE64_PUBLIC_KEY.isBlank()) return true
        return try {
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Base64.decode(BASE64_PUBLIC_KEY, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").apply {
                initVerify(publicKey)
                update(purchase.originalJson.toByteArray())
            }.verify(Base64.decode(purchase.signature, Base64.DEFAULT))
        } catch (e: Exception) {
            false
        }
    }
}
