package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ProxyDestination {
    @Serializable
    data object Dashboard : ProxyDestination

    @Serializable
    data object Profiles : ProxyDestination

    @Serializable
    data object PerApp : ProxyDestination

    @Serializable
    data object Logs : ProxyDestination
}
