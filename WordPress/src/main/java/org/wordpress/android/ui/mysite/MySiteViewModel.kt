package org.wordpress.android.ui.mysite

import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.modules.UI_THREAD
import androidx.lifecycle.MutableLiveData
import org.wordpress.android.analytics.AnalyticsTracker.Stat
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.config.MySiteDashboardTabsFeatureConfig
import org.wordpress.android.viewmodel.ScopedViewModel
import org.wordpress.android.viewmodel.Event
import javax.inject.Inject
import javax.inject.Named

class MySiteViewModel @Inject constructor(
    @param:Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val accountStore: AccountStore,
    private val buildConfigWrapper: BuildConfigWrapper,
    private val mySiteDashboardTabsFeatureConfig: MySiteDashboardTabsFeatureConfig
) : ScopedViewModel(mainDispatcher) {
    private val _onNavigation = MutableLiveData<Event<SiteNavigationAction>>()
    val onNavigation = _onNavigation as LiveData<Event<SiteNavigationAction>>

    val isMySiteTabsEnabled: Boolean
        get() = mySiteDashboardTabsFeatureConfig.isEnabled() && buildConfigWrapper.isMySiteTabsEnabled

    val tabTitles: List<UiString>
        get() = if (isMySiteTabsEnabled) {
            listOf(UiStringRes(R.string.my_site_menu_tab_title), UiStringRes(R.string.my_site_dashboard_tab_title))
        } else {
            listOf(UiStringRes(R.string.my_site_menu_tab_title))
        }

    fun onAvatarPressed() {
        _onNavigation.value = Event(SiteNavigationAction.OpenMeScreen)
    }

    fun onAddSitePressed() {
        _onNavigation.value = Event(SiteNavigationAction.AddNewSite(accountStore.hasAccessToken()))
        analyticsTrackerWrapper.track(Stat.MY_SITE_NO_SITES_VIEW_ACTION_TAPPED)
    }
}
