package org.wordpress.android.ui.mysite

import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.config.MySiteDashboardTabsFeatureConfig
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

class MySiteViewModel @Inject constructor(
    @param:Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val buildConfigWrapper: BuildConfigWrapper,
    private val mySiteDashboardTabsFeatureConfig: MySiteDashboardTabsFeatureConfig
) : ScopedViewModel(mainDispatcher) {
    val isMySiteTabsEnabled: Boolean
        get() = mySiteDashboardTabsFeatureConfig.isEnabled() && buildConfigWrapper.isMySiteTabsEnabled

    val tabTitles: List<UiString>
        get() = if (isMySiteTabsEnabled) {
            listOf(UiStringRes(R.string.my_site_menu_tab_title), UiStringRes(R.string.my_site_dashboard_tab_title))
        } else {
            listOf(UiStringRes(R.string.my_site_menu_tab_title))
        }
}
