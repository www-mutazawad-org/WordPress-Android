package org.wordpress.android.ui.mysite.cards

import org.wordpress.android.ui.mysite.MySiteCardAndItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DomainRegistrationCard
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.DashboardCardsBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.DomainRegistrationCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.QuickActionsCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.QuickStartCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.SiteInfoCardBuilderParams
import org.wordpress.android.ui.mysite.cards.dashboard.CardsBuilder
import org.wordpress.android.ui.mysite.cards.quickactions.QuickActionsCardBuilder
import org.wordpress.android.ui.mysite.cards.quickstart.QuickStartCardBuilder
import org.wordpress.android.ui.mysite.cards.siteinfo.SiteInfoCardBuilder
import org.wordpress.android.ui.utils.ListItemInteraction
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.config.MySiteDashboardPhase2FeatureConfig
import org.wordpress.android.util.config.QuickStartDynamicCardsFeatureConfig
import javax.inject.Inject

@Suppress("LongParameterList")
class CardsBuilder @Inject constructor(
    private val buildConfigWrapper: BuildConfigWrapper,
    private val quickStartDynamicCardsFeatureConfig: QuickStartDynamicCardsFeatureConfig,
    private val siteInfoCardBuilder: SiteInfoCardBuilder,
    private val quickActionsCardBuilder: QuickActionsCardBuilder,
    private val quickStartCardBuilder: QuickStartCardBuilder,
    private val dashboardCardsBuilder: CardsBuilder,
    private val mySiteDashboardPhase2FeatureConfig: MySiteDashboardPhase2FeatureConfig
) {
    fun build(
        siteInfoCardBuilderParams: SiteInfoCardBuilderParams? = null,
        quickActionsCardBuilderParams: QuickActionsCardBuilderParams? = null,
        domainRegistrationCardBuilderParams: DomainRegistrationCardBuilderParams? = null,
        quickStartCardBuilderParams: QuickStartCardBuilderParams? = null,
        dashboardCardsBuilderParams: DashboardCardsBuilderParams? = null
    ): List<MySiteCardAndItem> {
        val cards = mutableListOf<MySiteCardAndItem>()
        siteInfoCardBuilderParams?.let { cards.add(siteInfoCardBuilder.buildSiteInfoCard(it)) }
        if (buildConfigWrapper.isQuickActionEnabled) {
            quickActionsCardBuilderParams?.let { cards.add(quickActionsCardBuilder.build(it)) }
        }
        if (domainRegistrationCardBuilderParams?.isDomainCreditAvailable == true) {
            domainRegistrationCardBuilderParams?.let { cards.add(trackAndBuildDomainRegistrationCard(it)) }
        }
        if (!quickStartDynamicCardsFeatureConfig.isEnabled()) {
            quickStartCardBuilderParams?.quickStartCategories?.takeIf { it.isNotEmpty() }?.let {
                cards.add(quickStartCardBuilder.build(quickStartCardBuilderParams))
            }
        }
        if (mySiteDashboardPhase2FeatureConfig.isEnabled()) {
            dashboardCardsBuilderParams?.let { cards.add(dashboardCardsBuilder.build(it)) }
        }
        return cards
    }

    private fun trackAndBuildDomainRegistrationCard(
        params: DomainRegistrationCardBuilderParams
    ): DomainRegistrationCard {
        return DomainRegistrationCard(ListItemInteraction.create(params.domainRegistrationClick))
    }
}
