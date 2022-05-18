package org.wordpress.android.ui.deeplinks.handlers

import org.wordpress.android.ui.deeplinks.DeepLinkNavigator.NavigateAction
import org.wordpress.android.ui.deeplinks.DeepLinkNavigator.NavigateAction.OpenNotifications
import org.wordpress.android.ui.deeplinks.DeepLinkingIntentReceiverViewModel
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.UriWrapper
import javax.inject.Inject

class NotificationsLinkHandler
@Inject constructor(private val buildConfig: BuildConfigWrapper) : DeepLinkHandler {
    /**
     * Builds navigate action from URL like:
     * https://wordpress.com/notifications
     */
    override fun buildNavigateAction(uri: UriWrapper): NavigateAction {
        return OpenNotifications
    }

    /**
     * Returns true if the URI should be handled by NotificationsLinkHandler.
     * The handled links are `https://wordpress.com/notifications`
     */
    override fun shouldHandleUrl(uri: UriWrapper): Boolean {
        if (buildConfig.isJetpackApp && uri.toString().contains("notifications")) {
            return true
        } else {
            return (uri.host == DeepLinkingIntentReceiverViewModel.HOST_WORDPRESS_COM &&
                    uri.pathSegments.firstOrNull() == NOTIFICATIONS_PATH) || uri.host == NOTIFICATIONS_PATH
        }
    }

    override fun stripUrl(uri: UriWrapper): String {
         // todo: annmarie check if this is jetpack app
        return buildString {
            if (uri.host == NOTIFICATIONS_PATH) {
                if (buildConfig.isJetpackApp) {
                    append("jetpack://")
                } else {
                    append(DeepLinkingIntentReceiverViewModel.APPLINK_SCHEME)
                }
            } else {
                if (buildConfig.isJetpackApp) {
                    append("jetpack.com/")
                } else {
                    append("${DeepLinkingIntentReceiverViewModel.HOST_WORDPRESS_COM}/")
                }
            }
            append(NOTIFICATIONS_PATH)
        }
    }

    companion object {
        private const val NOTIFICATIONS_PATH = "notifications"
    }
}
