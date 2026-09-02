package com.factory.timetilescountdownwidgets.billing

sealed class BillingConnectionState {
    data object Connecting : BillingConnectionState()
    data object Connected : BillingConnectionState()
    data object Disconnected : BillingConnectionState()
    data class Error(val message: String) : BillingConnectionState()
}

sealed class PurchaseEvent {
    data class Success(val productIds: List<String>) : PurchaseEvent()
    data object Cancelled : PurchaseEvent()
    data object Pending : PurchaseEvent()
    data object NetworkError : PurchaseEvent()
    data class Error(val message: String) : PurchaseEvent()
}
