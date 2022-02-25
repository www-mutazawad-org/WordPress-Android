package org.wordpress.android.ui.mysite

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker.Stat
import org.wordpress.android.analytics.AnalyticsTracker.Stat.MY_SITE_PULL_TO_REFRESH
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.PagePostCreationSourcesDetail.STORY_FROM_MY_SITE
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.SiteInfoCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteUiState.PartialState
import org.wordpress.android.ui.mysite.MySiteViewModel.State.NoSites
import org.wordpress.android.ui.mysite.MySiteViewModel.State.SiteSelected
import org.wordpress.android.ui.mysite.SiteDialogModel.AddSiteIconDialogModel
import org.wordpress.android.ui.mysite.SiteDialogModel.ChangeSiteIconDialogModel
import org.wordpress.android.ui.mysite.cards.CardsBuilder
import org.wordpress.android.ui.mysite.cards.quickstart.QuickStartRepository
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.photopicker.PhotoPickerActivity.PhotoPickerMediaSource
import org.wordpress.android.ui.photopicker.PhotoPickerActivity.PhotoPickerMediaSource.ANDROID_CAMERA
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Dismissed
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Negative
import org.wordpress.android.ui.posts.BasicDialogViewModel.DialogInteraction.Positive
import org.wordpress.android.ui.utils.UiString.UiStringRes
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.DisplayUtilsWrapper
import org.wordpress.android.util.FluxCUtilsWrapper
import org.wordpress.android.util.MediaUtilsWrapper
import org.wordpress.android.util.NetworkUtilsWrapper
import org.wordpress.android.util.QuickStartUtilsWrapper
import org.wordpress.android.util.SiteUtils
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.util.WPMediaUtilsWrapper
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.util.config.MySiteDashboardPhase2FeatureConfig
import org.wordpress.android.util.config.QuickStartDynamicCardsFeatureConfig
import org.wordpress.android.util.experiments.LandOnTheEditorABExperiment
import org.wordpress.android.util.filter
import org.wordpress.android.util.getEmailValidationMessage
import org.wordpress.android.util.map
import org.wordpress.android.util.merge
import org.wordpress.android.viewmodel.ContextProvider
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@Suppress("LargeClass", "LongMethod", "LongParameterList", "TooManyFunctions")
class MySiteViewModel @Inject constructor(
    private val networkUtilsWrapper: NetworkUtilsWrapper,
    @param:Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    @param:Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val accountStore: AccountStore,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val wpMediaUtilsWrapper: WPMediaUtilsWrapper,
    private val mediaUtilsWrapper: MediaUtilsWrapper,
    private val fluxCUtilsWrapper: FluxCUtilsWrapper,
    private val contextProvider: ContextProvider,
    private val siteIconUploadHandler: SiteIconUploadHandler,
    private val siteStoriesHandler: SiteStoriesHandler,
    private val displayUtilsWrapper: DisplayUtilsWrapper,
    private val quickStartRepository: QuickStartRepository,
    private val homePageDataLoader: HomePageDataLoader,
    private val quickStartDynamicCardsFeatureConfig: QuickStartDynamicCardsFeatureConfig,
    private val quickStartUtilsWrapper: QuickStartUtilsWrapper,
    private val cardsBuilder: CardsBuilder,
    private val landOnTheEditorABExperiment: LandOnTheEditorABExperiment,
    private val mySiteDashboardPhase2FeatureConfig: MySiteDashboardPhase2FeatureConfig,
    private val mySiteSourceManager: MySiteSourceManager,
    private val buildConfigWrapper: BuildConfigWrapper
) : ScopedViewModel(mainDispatcher) {
    private val _onSnackbarMessage = MutableLiveData<Event<SnackbarMessageHolder>>()
    private val _onTechInputDialogShown = MutableLiveData<Event<TextInputDialogModel>>()
    private val _onBasicDialogShown = MutableLiveData<Event<SiteDialogModel>>()
    private val _onNavigation = MutableLiveData<Event<SiteNavigationAction>>()
    private val _onMediaUpload = MutableLiveData<Event<MediaModel>>()
    private val _activeTaskPosition = MutableLiveData<Pair<QuickStartTask, Int>>()
    private val _onShowSwipeRefreshLayout = MutableLiveData<Event<Boolean>>()

    /* Capture and track the site selected event so we can circumvent refreshing sources on resume
       as they're already built on site select. */
    private var isSiteSelected = false

    val onSnackbarMessage = merge(_onSnackbarMessage, siteStoriesHandler.onSnackbar, quickStartRepository.onSnackbar)
    val onTextInputDialogShown = _onTechInputDialogShown as LiveData<Event<TextInputDialogModel>>
    val onBasicDialogShown = _onBasicDialogShown as LiveData<Event<SiteDialogModel>>
    val onNavigation = merge(_onNavigation, siteStoriesHandler.onNavigation)
    val onMediaUpload = _onMediaUpload as LiveData<Event<MediaModel>>
    val onUploadedItem = siteIconUploadHandler.onUploadedItem
    val onShowSwipeRefreshLayout = _onShowSwipeRefreshLayout

    val state: LiveData<MySiteUiState> =
            selectedSiteRepository.siteSelected.switchMap { siteLocalId ->
                isSiteSelected = true
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
            currentAvatarUrl,
            site,
            showSiteIconProgressBar,
            _,
            _,
            _,
            activeTask,
            _,
            _,
            _,
            _
    ) ->
        val state = if (site != null) {
            val state = buildSiteSelectedStateAndScroll(
                    site,
                    showSiteIconProgressBar,
                    activeTask
            )
            state
        } else {
            buildNoSiteState()
        }
        UiModel(currentAvatarUrl.orEmpty(), state)
    }

    private fun buildSiteSelectedStateAndScroll(
        site: SiteModel,
        showSiteIconProgressBar: Boolean,
        activeTask: QuickStartTask?
    ): SiteSelected {
        val siteItems = buildSiteSelectedState(
                site,
                showSiteIconProgressBar,
                activeTask
        )
        scrollToQuickStartTaskIfNecessary(
                activeTask,
                siteItems.indexOfFirst { it.activeQuickStartItem }
        )
        return SiteSelected(siteItems)
    }

    private fun buildSiteSelectedState(
        site: SiteModel,
        showSiteIconProgressBar: Boolean,
        activeTask: QuickStartTask?
    ): List<MySiteCardAndItem> {
        return cardsBuilder.build(
                SiteInfoCardBuilderParams(
                        site = site,
                        showSiteIconProgressBar = showSiteIconProgressBar,
                        titleClick = this::titleClick,
                        iconClick = this::iconClick,
                        urlClick = this::urlClick,
                        switchSiteClick = this::switchSiteClick,
                        activeTask = activeTask
                )
        )
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

    private fun titleClick() {
        val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
        if (!networkUtilsWrapper.isNetworkAvailable()) {
            _onSnackbarMessage.value = Event(SnackbarMessageHolder(UiStringRes(R.string.error_network_connection)))
        } else if (!SiteUtils.isAccessedViaWPComRest(selectedSite) || !selectedSite.hasCapabilityManageOptions) {
            _onSnackbarMessage.value = Event(
                    SnackbarMessageHolder(UiStringRes(R.string.my_site_title_changer_dialog_not_allowed_hint))
            )
        } else {
            _onTechInputDialogShown.value = Event(
                    TextInputDialogModel(
                            callbackId = SITE_NAME_CHANGE_CALLBACK_ID,
                            title = R.string.my_site_title_changer_dialog_title,
                            initialText = selectedSite.name,
                            hint = R.string.my_site_title_changer_dialog_hint,
                            isMultiline = false,
                            isInputEnabled = true
                    )
            )
        }
    }

    private fun iconClick() {
        val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
        analyticsTrackerWrapper.track(Stat.MY_SITE_ICON_TAPPED)
        val hasIcon = selectedSite.iconUrl != null
        if (selectedSite.hasCapabilityManageOptions && selectedSite.hasCapabilityUploadFiles) {
            if (hasIcon) {
                _onBasicDialogShown.value = Event(ChangeSiteIconDialogModel)
            } else {
                _onBasicDialogShown.value = Event(AddSiteIconDialogModel)
            }
        } else {
            val message = when {
                !selectedSite.isUsingWpComRestApi -> {
                    R.string.my_site_icon_dialog_change_requires_jetpack_message
                }
                hasIcon -> {
                    R.string.my_site_icon_dialog_change_requires_permission_message
                }
                else -> {
                    R.string.my_site_icon_dialog_add_requires_permission_message
                }
            }
            _onSnackbarMessage.value = Event(SnackbarMessageHolder(UiStringRes(message)))
        }
    }

    private fun urlClick() {
        val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
        _onNavigation.value = Event(SiteNavigationAction.OpenSite(selectedSite))
    }

    private fun switchSiteClick() {
        val selectedSite = requireNotNull(selectedSiteRepository.getSelectedSite())
        _onNavigation.value = Event(SiteNavigationAction.OpenSitePicker(selectedSite))
    }

    fun refresh(isPullToRefresh: Boolean = false) {
        if (isPullToRefresh) analyticsTrackerWrapper.track(MY_SITE_PULL_TO_REFRESH)
        mySiteSourceManager.refresh()
    }

    fun onResume() {
        mySiteSourceManager.onResume(isSiteSelected)
        isSiteSelected = false
        _onShowSwipeRefreshLayout.postValue(Event(mySiteDashboardPhase2FeatureConfig.isEnabled()))
    }

    fun onSiteNameChosen(input: String) {
        if (!networkUtilsWrapper.isNetworkAvailable()) {
            _onSnackbarMessage.postValue(
                    Event(SnackbarMessageHolder(UiStringRes(R.string.error_update_site_title_network)))
            )
        } else {
            selectedSiteRepository.updateTitle(input)
        }
    }

    fun onSiteNameChooserDismissed() {
        // This callback is called even when the dialog interaction is positive,
        // otherwise we would need to call 'completeTask' on 'onSiteNameChosen' as well.
        quickStartRepository.completeTask(QuickStartTask.UPDATE_SITE_TITLE)
        quickStartRepository.checkAndShowQuickStartNotice()
    }

    fun onDialogInteraction(interaction: DialogInteraction) {
        when (interaction) {
            is Positive -> when (interaction.tag) {
                TAG_ADD_SITE_ICON_DIALOG, TAG_CHANGE_SITE_ICON_DIALOG -> {
                    quickStartRepository.completeTask(QuickStartTask.UPLOAD_SITE_ICON)
                    _onNavigation.postValue(
                            Event(
                                    SiteNavigationAction.OpenMediaPicker(
                                            requireNotNull(selectedSiteRepository.getSelectedSite())
                                    )
                            )
                    )
                }
            }
            is Negative -> when (interaction.tag) {
                TAG_ADD_SITE_ICON_DIALOG -> {
                    quickStartRepository.completeTask(QuickStartTask.UPLOAD_SITE_ICON)
                    quickStartRepository.checkAndShowQuickStartNotice()
                }
                TAG_CHANGE_SITE_ICON_DIALOG -> {
                    analyticsTrackerWrapper.track(Stat.MY_SITE_ICON_REMOVED)
                    quickStartRepository.completeTask(QuickStartTask.UPLOAD_SITE_ICON)
                    quickStartRepository.checkAndShowQuickStartNotice()
                    selectedSiteRepository.updateSiteIconMediaId(0, true)
                }
            }
            is Dismissed -> when (interaction.tag) {
                TAG_ADD_SITE_ICON_DIALOG, TAG_CHANGE_SITE_ICON_DIALOG -> {
                    quickStartRepository.completeTask(QuickStartTask.UPLOAD_SITE_ICON)
                    quickStartRepository.checkAndShowQuickStartNotice()
                }
            }
        }
    }

    fun handleTakenSiteIcon(iconUrl: String?, source: PhotoPickerMediaSource?) {
        val stat = if (source == ANDROID_CAMERA) Stat.MY_SITE_ICON_SHOT_NEW else Stat.MY_SITE_ICON_GALLERY_PICKED
        analyticsTrackerWrapper.track(stat)
        val imageUri = Uri.parse(iconUrl)?.let { UriWrapper(it) }
        if (imageUri != null) {
            selectedSiteRepository.showSiteIconProgressBar(true)
            launch(bgDispatcher) {
                val fetchMedia = wpMediaUtilsWrapper.fetchMediaToUriWrapper(imageUri)
                if (fetchMedia != null) {
                    _onNavigation.postValue(Event(SiteNavigationAction.OpenCropActivity(fetchMedia)))
                } else {
                    selectedSiteRepository.showSiteIconProgressBar(false)
                }
            }
        }
    }

    fun handleSelectedSiteIcon(mediaId: Long) {
        selectedSiteRepository.updateSiteIconMediaId(mediaId.toInt(), true)
    }

    fun handleCropResult(croppedUri: Uri?, success: Boolean) {
        if (success && croppedUri != null) {
            analyticsTrackerWrapper.track(Stat.MY_SITE_ICON_CROPPED)
            selectedSiteRepository.showSiteIconProgressBar(true)
            launch(bgDispatcher) {
                wpMediaUtilsWrapper.fetchMediaToUriWrapper(UriWrapper(croppedUri))?.let { fetchMedia ->
                    mediaUtilsWrapper.getRealPathFromURI(fetchMedia.uri)
                }?.let {
                    startSiteIconUpload(it)
                }
            }
        } else {
            _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.error_cropping_image))))
        }
    }

    fun handleSuccessfulLoginResult() {
        selectedSiteRepository.getSelectedSite()?.let { site ->
            _onNavigation.value = Event(
                    SiteNavigationAction.OpenStats(site)
            )
        }
    }

    fun handleSuccessfulDomainRegistrationResult(email: String?) {
        analyticsTrackerWrapper.track(Stat.DOMAIN_CREDIT_REDEMPTION_SUCCESS)
        _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(getEmailValidationMessage(email))))
    }

    @Suppress("ReturnCount")
    private fun startSiteIconUpload(filePath: String) {
        if (TextUtils.isEmpty(filePath)) {
            _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.error_locating_image))))
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.file_error_create))))
            return
        }
        val selectedSite = selectedSiteRepository.getSelectedSite()
        if (selectedSite != null) {
            val media = buildMediaModel(file, selectedSite)
            if (media == null) {
                _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.file_not_found))))
                return
            }
            _onMediaUpload.postValue(Event(media))
        } else {
            _onSnackbarMessage.postValue(Event(SnackbarMessageHolder(UiStringRes(R.string.error_generic))))
        }
    }

    private fun buildMediaModel(file: File, site: SiteModel): MediaModel? {
        val uri = Uri.Builder().path(file.path).build()
        val mimeType = contextProvider.getContext().contentResolver.getType(uri)
        return fluxCUtilsWrapper.mediaModelFromLocalUri(uri, mimeType, site.id)
    }

    fun onAvatarPressed() {
        _onNavigation.value = Event(SiteNavigationAction.OpenMeScreen)
    }

    fun onAddSitePressed() {
        _onNavigation.value = Event(SiteNavigationAction.AddNewSite(accountStore.hasAccessToken()))
        analyticsTrackerWrapper.track(Stat.MY_SITE_NO_SITES_VIEW_ACTION_TAPPED)
    }

    override fun onCleared() {
        siteIconUploadHandler.clear()
        siteStoriesHandler.clear()
        quickStartRepository.clear()
        mySiteSourceManager.clear()
        super.onCleared()
    }

    fun handleStoriesPhotoPickerResult(data: Intent) {
        selectedSiteRepository.getSelectedSite()?.let {
            siteStoriesHandler.handleStoriesResult(it, data, STORY_FROM_MY_SITE)
        }
    }

    fun performFirstStepAfterSiteCreation(siteLocalId: Int) {
        // TODO: ANNMARIE ignore this for the POC - I'm tired of seeing the editor open
       // if (landOnTheEditorABExperiment.getVariation() is Control) {
            checkAndStartQuickStart(siteLocalId)
//        } else {
//            checkAndStartLandOnTheEditor()
//        }
    }

    fun checkAndStartLandOnTheEditor() {
        selectedSiteRepository.getSelectedSite()?.let { selectedSite ->
            launch(bgDispatcher) {
                homePageDataLoader.loadHomepage(selectedSite)?.pageId?.let { localHomepageId ->
                    val landOnTheEditorAction = SiteNavigationAction.OpenHomepage(selectedSite, localHomepageId)
                    _onNavigation.postValue(Event(landOnTheEditorAction))
                    analyticsTrackerWrapper.track(Stat.LANDING_EDITOR_SHOWN)
                }
            }
        }
    }

    fun checkAndStartQuickStart(siteLocalId: Int) {
        if (quickStartDynamicCardsFeatureConfig.isEnabled()) {
            startQuickStart(siteLocalId)
        } else {
            showQuickStartDialog(selectedSiteRepository.getSelectedSite())
        }
    }

    private fun startQuickStart(siteLocalId: Int) {
        if (siteLocalId != SelectedSiteRepository.UNAVAILABLE) {
            quickStartUtilsWrapper.startQuickStart(siteLocalId)
            mySiteSourceManager.refreshQuickStart()
        }
    }

    private fun showQuickStartDialog(siteModel: SiteModel?) {
        if (siteModel != null && quickStartUtilsWrapper.isQuickStartAvailableForTheSite(siteModel)) {
            _onNavigation.postValue(
                    Event(
                            SiteNavigationAction.ShowQuickStartDialog(
                                    R.string.quick_start_dialog_need_help_manage_site_title,
                                    R.string.quick_start_dialog_need_help_manage_site_message,
                                    R.string.quick_start_dialog_need_help_manage_site_button_positive,
                                    R.string.quick_start_dialog_need_help_button_negative
                            )
                    )
            )
        }
    }

    fun isRefreshing() = mySiteSourceManager.isRefreshing()

    fun setActionableEmptyViewGone(isVisible: Boolean, setGone: () -> Unit) {
        if (isVisible) analyticsTrackerWrapper.track(Stat.MY_SITE_NO_SITES_VIEW_HIDDEN)
        setGone()
    }

    fun setActionableEmptyViewVisible(isVisible: Boolean, setVisible: () -> Unit) {
        if (!isVisible) analyticsTrackerWrapper.track(Stat.MY_SITE_NO_SITES_VIEW_DISPLAYED)
        setVisible()
    }

    data class UiModel(
        val accountAvatarUrl: String,
        val state: State
    )

    sealed class State {
        data class SiteSelected(val cardAndItems: List<MySiteCardAndItem>) : State()
        data class NoSites(val shouldShowImage: Boolean) : State()
    }

    data class TextInputDialogModel(
        val callbackId: Int = SITE_NAME_CHANGE_CALLBACK_ID,
        @StringRes val title: Int,
        val initialText: String,
        @StringRes val hint: Int,
        val isMultiline: Boolean,
        val isInputEnabled: Boolean
    )

    private data class SiteIdToState(val siteId: Int?, val state: MySiteUiState = MySiteUiState()) {
        fun update(partialState: PartialState): SiteIdToState {
            return this.copy(state = state.update(partialState))
        }
    }

    companion object {
        private const val MIN_DISPLAY_PX_HEIGHT_NO_SITE_IMAGE = 600
        const val TAG_ADD_SITE_ICON_DIALOG = "TAG_ADD_SITE_ICON_DIALOG"
        const val TAG_CHANGE_SITE_ICON_DIALOG = "TAG_CHANGE_SITE_ICON_DIALOG"
        const val TAG_REMOVE_NEXT_STEPS_DIALOG = "TAG_REMOVE_NEXT_STEPS_DIALOG"
        const val SITE_NAME_CHANGE_CALLBACK_ID = 1
        const val ARG_QUICK_START_TASK = "ARG_QUICK_START_TASK"
        const val HIDE_WP_ADMIN_GMT_TIME_ZONE = "GMT"
    }
}
