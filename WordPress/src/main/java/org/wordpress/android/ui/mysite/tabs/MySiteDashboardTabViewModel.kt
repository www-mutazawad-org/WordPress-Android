package org.wordpress.android.ui.mysite.tabs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MY_SITE_PULL_TO_REFRESH
import org.wordpress.android.fluxc.model.dashboard.CardModel.PostsCardModel
import org.wordpress.android.fluxc.model.dashboard.CardModel.TodaysStatsCardModel
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.mysite.MySiteCardAndItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Item.InfoItem
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.DashboardCardsBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.InfoItemBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.PostCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.PostCardBuilderParams.PostItemClickParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.TodaysStatsCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteSourceManager
import org.wordpress.android.ui.mysite.MySiteUiState
import org.wordpress.android.ui.mysite.MySiteUiState.PartialState
import org.wordpress.android.ui.mysite.MySiteUiState.PartialState.CardsUpdate
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.mysite.SiteNavigationAction
import org.wordpress.android.ui.mysite.addDistinctUntilChangedIfNeeded
import org.wordpress.android.ui.mysite.cards.CardsBuilder
import org.wordpress.android.ui.mysite.cards.dashboard.CardsTracker
import org.wordpress.android.ui.mysite.cards.dashboard.posts.PostCardType
import org.wordpress.android.ui.mysite.cards.quickstart.QuickStartRepository
import org.wordpress.android.ui.mysite.items.SiteItemsBuilder
import org.wordpress.android.ui.mysite.tabs.MySiteDashboardTabViewModel.State.NoSites
import org.wordpress.android.ui.mysite.tabs.MySiteDashboardTabViewModel.State.SiteSelected
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.DisplayUtilsWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.config.MySiteDashboardPhase2FeatureConfig
import org.wordpress.android.util.filter
import org.wordpress.android.util.map
import org.wordpress.android.util.merge
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import javax.inject.Inject
import javax.inject.Named

@Suppress("LargeClass", "LongMethod", "LongParameterList", "TooManyFunctions")
class MySiteDashboardTabViewModel @Inject constructor(
    @param:Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val siteItemsBuilder: SiteItemsBuilder,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val displayUtilsWrapper: DisplayUtilsWrapper,
    private val quickStartRepository: QuickStartRepository,
    private val cardsBuilder: CardsBuilder,
    private val mySiteDashboardPhase2FeatureConfig: MySiteDashboardPhase2FeatureConfig,
    private val mySiteSourceManager: MySiteSourceManager,
    private val cardsTracker: CardsTracker,
    private val buildConfigWrapper: BuildConfigWrapper
) : ScopedViewModel(mainDispatcher) {
    private val _onSnackbarMessage = MutableLiveData<Event<SnackbarMessageHolder>>()
    private val _onNavigation = MutableLiveData<Event<SiteNavigationAction>>()
    private val _activeTaskPosition = MutableLiveData<Pair<QuickStartTask, Int>>()

    /* Capture and track the site selected event so we can circumvent refreshing sources on resume
       as they're already built on site select. */
    private var isSiteSelected = false

    val onSnackbarMessage = merge(_onSnackbarMessage, quickStartRepository.onSnackbar)
    val onNavigation: LiveData<Event<SiteNavigationAction>> = _onNavigation

    val state: LiveData<MySiteUiState> =
            selectedSiteRepository.siteSelected.switchMap { siteLocalId ->
                isSiteSelected = true
                resetShownTrackers()
                val result = MediatorLiveData<SiteIdToState>()
                for (newSource in mySiteSourceManager.build(viewModelScope, siteLocalId)) {
                    result.addSource(newSource) { partialState ->
                        if (partialState != null) {
                            result.value = (result.value ?: SiteIdToState(siteLocalId)).update(partialState)
                        }
                    }
                }
                // We want to filter out the empty state where we have a site ID but site object is missing.
                // Without this check there is an emission of a NoSites state even if we have the site
                result.filter { it.siteId == null || it.state.site != null }.map { it.state }
            }.addDistinctUntilChangedIfNeeded(!mySiteDashboardPhase2FeatureConfig.isEnabled())

    val uiModel: LiveData<UiModel> = state.map { (
            _,
            site,
            _,
            _,
            _,
            _,
            activeTask,
            _,
            _,
            _,
            cardsUpdate
    ) ->
        val state = if (site != null) {
            cardsUpdate?.checkAndShowSnackbarError()
            val state = buildSiteSelectedStateAndScroll(
                    activeTask,
                    cardsUpdate
            )
            trackCardsAndItemsShownIfNeeded(state)
            state
        } else {
            buildNoSiteState()
        }
        UiModel("", state)
    }

    private fun CardsUpdate.checkAndShowSnackbarError() {
        if (showSnackbarError) {
            _onSnackbarMessage
                    .postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.my_site_dashboard_update_error))))
        }
    }

    private fun buildSiteSelectedStateAndScroll(
        activeTask: QuickStartTask?,
        cardsUpdate: CardsUpdate?
    ): SiteSelected {
        val siteItems = buildSiteSelectedState(
                cardsUpdate
        )
        scrollToQuickStartTaskIfNecessary(
                activeTask,
                siteItems.indexOfFirst { it.activeQuickStartItem }
        )
        return SiteSelected(siteItems)
    }

    private fun buildSiteSelectedState(
        cardsUpdate: CardsUpdate?
    ): List<MySiteCardAndItem> {
        val infoItem = siteItemsBuilder.build(
                InfoItemBuilderParams(
                        isStaleMessagePresent = cardsUpdate?.showStaleMessage ?: false
                )
        )
        val cardsResult = cardsBuilder.build(
                dashboardCardsBuilderParams = DashboardCardsBuilderParams(
                        showErrorCard = cardsUpdate?.showErrorCard == true,
                        onErrorRetryClick = this::onDashboardErrorRetry,
                        todaysStatsCardBuilderParams = TodaysStatsCardBuilderParams(
                                todaysStatsCard = cardsUpdate?.cards?.firstOrNull { it is TodaysStatsCardModel }
                                        as? TodaysStatsCardModel,
                                onTodaysStatsCardClick = this::onTodaysStatsCardClick,
                                onFooterLinkClick = this::onTodaysStatsCardFooterLinkClick
                        ),
                        postCardBuilderParams = PostCardBuilderParams(
                                posts = cardsUpdate?.cards?.firstOrNull { it is PostsCardModel } as? PostsCardModel,
                                onPostItemClick = this::onPostItemClick,
                                onFooterLinkClick = this::onPostCardFooterLinkClick
                        )
                )
        )
        return orderForDisplay(infoItem, cardsResult)
    }

    private fun orderForDisplay(
        infoItem: InfoItem?,
        cards: List<MySiteCardAndItem>
    ): List<MySiteCardAndItem> {
        return mutableListOf<MySiteCardAndItem>().apply {
            infoItem?.let { add(infoItem) }
            addAll(cards)
        }.toList()
    }

    private fun onTodaysStatsCardFooterLinkClick() {
        cardsTracker.trackTodaysStatsCardFooterLinkClicked()
        navigateToTodaysStats()
    }

    private fun onTodaysStatsCardClick() {
        cardsTracker.trackTodaysStatsCardClicked()
        navigateToTodaysStats()
    }

    private fun navigateToTodaysStats() {
        val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
        _onNavigation.value = Event(SiteNavigationAction.OpenTodaysStats(selectedSite))
    }

    private fun buildNoSiteState(): NoSites {
        // Hide actionable empty view image when screen height is under specified min height.
        val shouldShowImage = !buildConfigWrapper.isJetpackApp &&
                displayUtilsWrapper.getDisplayPixelHeight() >= MIN_DISPLAY_PX_HEIGHT_NO_SITE_IMAGE
        return NoSites(shouldShowImage)
    }

    private fun scrollToQuickStartTaskIfNecessary(
        quickStartTask: QuickStartTask?,
        position: Int
    ) {
        if (quickStartTask == null) {
            _activeTaskPosition.postValue(null)
        } else if (_activeTaskPosition.value?.first != quickStartTask && position >= 0) {
            _activeTaskPosition.postValue(quickStartTask to position)
        }
    }

    fun refresh(isPullToRefresh: Boolean = false) {
        if (isPullToRefresh) analyticsTrackerWrapper.track(MY_SITE_PULL_TO_REFRESH)
        mySiteSourceManager.refresh()
    }

    fun onResume() {
        mySiteSourceManager.onResume(isSiteSelected)
        isSiteSelected = false
//        _onShowSwipeRefreshLayout.postValue(Event(mySiteDashboardPhase2FeatureConfig.isEnabled()))
    }

    override fun onCleared() {
        quickStartRepository.clear()
        mySiteSourceManager.clear()
        super.onCleared()
    }

    private fun onPostItemClick(params: PostItemClickParams) {
        selectedSiteRepository.getSelectedSite()?.let { site ->
            cardsTracker.trackPostItemClicked(params.postCardType)
            when (params.postCardType) {
                PostCardType.DRAFT -> _onNavigation.value =
                        Event(SiteNavigationAction.EditDraftPost(site, params.postId))
                PostCardType.SCHEDULED -> _onNavigation.value =
                        Event(SiteNavigationAction.EditScheduledPost(site, params.postId))
                else -> Unit // Do nothing
            }
        }
    }

    private fun onDashboardErrorRetry() {
        mySiteSourceManager.refresh()
    }

    private fun onPostCardFooterLinkClick(postCardType: PostCardType) {
        selectedSiteRepository.getSelectedSite()?.let { site ->
            cardsTracker.trackPostCardFooterLinkClicked(postCardType)
            _onNavigation.value = when (postCardType) {
                PostCardType.CREATE_FIRST, PostCardType.CREATE_NEXT ->
                    Event(SiteNavigationAction.OpenEditorToCreateNewPost(site))
                PostCardType.DRAFT -> Event(SiteNavigationAction.OpenDraftsPosts(site))
                PostCardType.SCHEDULED -> Event(SiteNavigationAction.OpenScheduledPosts(site))
            }
        }
    }

    fun isRefreshing() = mySiteSourceManager.isRefreshing()

    private fun trackCardsAndItemsShownIfNeeded(siteSelected: SiteSelected) {
        siteSelected.cardAndItems.filterIsInstance<DashboardCards>().forEach { cardsTracker.trackShown(it) }
    }

    private fun resetShownTrackers() {
        cardsTracker.resetShown()
    }

    data class UiModel(
        val accountAvatarUrl: String,
        val state: State
    )

    sealed class State {
        data class SiteSelected(val cardAndItems: List<MySiteCardAndItem>) : State()
        data class NoSites(val shouldShowImage: Boolean) : State()
    }

    private data class SiteIdToState(val siteId: Int?, val state: MySiteUiState = MySiteUiState()) {
        fun update(partialState: PartialState): SiteIdToState {
            return this.copy(state = state.update(partialState))
        }
    }

    companion object {
        private const val MIN_DISPLAY_PX_HEIGHT_NO_SITE_IMAGE = 600
        const val TAG_ADD_SITE_ICON_DIALOG = "TAG_ADD_SITE_ICON_DIALOG"
        const val SITE_NAME_CHANGE_CALLBACK_ID = 1
    }
}

