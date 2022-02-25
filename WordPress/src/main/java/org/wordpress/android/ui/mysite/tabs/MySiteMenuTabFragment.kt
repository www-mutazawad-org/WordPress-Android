package org.wordpress.android.ui.mysite.tabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.wordpress.android.R
import org.wordpress.android.R.dimen
import org.wordpress.android.WordPress
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.databinding.MySiteMenuTabFragmentBinding
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.FullScreenDialogFragment
import org.wordpress.android.ui.FullScreenDialogFragment.Builder
import org.wordpress.android.ui.FullScreenDialogFragment.OnConfirmListener
import org.wordpress.android.ui.FullScreenDialogFragment.OnDismissListener
import org.wordpress.android.ui.PagePostCreationSourcesDetail
import org.wordpress.android.ui.RequestCodes
import org.wordpress.android.ui.domains.DomainRegistrationActivity
import org.wordpress.android.ui.domains.DomainRegistrationActivity.DomainRegistrationPurpose.CTA_DOMAIN_CREDIT_REDEMPTION
import org.wordpress.android.ui.main.SitePickerActivity
import org.wordpress.android.ui.mysite.MySiteAction
import org.wordpress.android.ui.mysite.MySiteActionHandler
import org.wordpress.android.ui.mysite.MySiteAdapter
import org.wordpress.android.ui.mysite.MySiteCardAndItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.SiteInfoCard
import org.wordpress.android.ui.mysite.MySiteCardAndItemDecoration
import org.wordpress.android.ui.mysite.MySiteViewModel
import org.wordpress.android.ui.mysite.MySiteViewModel.State
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.mysite.SiteNavigationAction
import org.wordpress.android.ui.mysite.dynamiccards.DynamicCardMenuFragment
import org.wordpress.android.ui.mysite.dynamiccards.DynamicCardMenuViewModel
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.photopicker.MediaPickerLauncher
import org.wordpress.android.ui.posts.PostListType
import org.wordpress.android.ui.posts.QuickStartPromptDialogFragment
import org.wordpress.android.ui.quickstart.QuickStartFullScreenDialogFragment
import org.wordpress.android.ui.stats.StatsTimeframe
import org.wordpress.android.ui.uploads.UploadUtilsWrapper
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.ui.utils.UiString.UiStringText
import org.wordpress.android.util.QuickStartUtilsWrapper
import org.wordpress.android.util.SnackbarItem
import org.wordpress.android.util.SnackbarItem.Action
import org.wordpress.android.util.SnackbarItem.Info
import org.wordpress.android.util.SnackbarSequencer
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.util.setVisible
import org.wordpress.android.viewmodel.observeEvent
import javax.inject.Inject

class MySiteMenuTabFragment : Fragment(R.layout.my_site_menu_tab_fragment),
        OnConfirmListener,
        OnDismissListener,
        MySiteActionHandler {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var uiHelpers: UiHelpers
    @Inject lateinit var snackbarSequencer: SnackbarSequencer
    @Inject lateinit var mediaPickerLauncher: MediaPickerLauncher
    @Inject lateinit var uploadUtilsWrapper: UploadUtilsWrapper
    @Inject lateinit var quickStartUtils: QuickStartUtilsWrapper
    private lateinit var mySiteViewModel: MySiteViewModel
    private lateinit var dynamicCardMenuViewModel: DynamicCardMenuViewModel
    private lateinit var viewModel: MySiteMenuTabViewModel
    private var binding: MySiteMenuTabFragmentBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        initDagger()
        initViewModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MySiteMenuTabFragmentBinding.bind(view).apply {
            setupContentViews(savedInstanceState)
            setupObservers()
            // todo: note: swipeToRefreshHelper.isRefreshing = true
        }
    }

    private fun initDagger() {
        (requireActivity().application as WordPress).component().inject(this)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(MySiteMenuTabViewModel::class.java)
        mySiteViewModel = ViewModelProvider(this, viewModelFactory).get(MySiteViewModel::class.java)
        dynamicCardMenuViewModel = ViewModelProvider(requireActivity(), viewModelFactory)
                .get(DynamicCardMenuViewModel::class.java)
    }

    private fun MySiteMenuTabFragmentBinding.setupContentViews(savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)

        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(
                MySiteCardAndItemDecoration(
                        horizontalMargin = resources.getDimensionPixelSize(dimen.margin_extra_large),
                        verticalMargin = resources.getDimensionPixelSize(dimen.margin_medium)
                )
        )

        val adapter = MySiteAdapter(imageManager, uiHelpers)

        savedInstanceState?.getBundle(KEY_NESTED_LISTS_STATES)?.let {
            adapter.onRestoreInstanceState(it)
        }

        recyclerView.adapter = adapter
    }

    @Suppress("LongMethod")
    private fun MySiteMenuTabFragmentBinding.setupObservers() {
        mySiteViewModel.uiModel.observe(viewLifecycleOwner, { uiModel ->
            // todo:  hideRefreshIndicatorIfNeeded()
            when (val state = uiModel.state) {
                is State.SiteSelected -> loadData(state.cardAndItems)
            }
        })
        mySiteViewModel.onScrollTo.observeEvent(viewLifecycleOwner, {
            (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(it, 0)
        })
        mySiteViewModel.onDynamicCardMenuShown.observeEvent(viewLifecycleOwner, { dynamicCardMenuModel ->
            ((parentFragmentManager.findFragmentByTag(dynamicCardMenuModel.id) as? DynamicCardMenuFragment)
                    ?: DynamicCardMenuFragment.newInstance(
                            dynamicCardMenuModel.cardType,
                            dynamicCardMenuModel.isPinned
                    ))
                    .show(parentFragmentManager, dynamicCardMenuModel.id)
        })
        mySiteViewModel.onNavigation.observeEvent(viewLifecycleOwner, { handleNavigationAction(it) })
        mySiteViewModel.onSnackbarMessage.observeEvent(viewLifecycleOwner, { showSnackbar(it) })
        mySiteViewModel.onQuickStartMySitePrompts.observeEvent(viewLifecycleOwner, { activeTutorialPrompt ->
            val message = quickStartUtils.stylizeQuickStartPrompt(
                    requireContext(),
                    activeTutorialPrompt.shortMessagePrompt,
                    activeTutorialPrompt.iconId
            )
            showSnackbar(SnackbarMessageHolder(UiStringText(message)))
        })
        dynamicCardMenuViewModel.onInteraction.observeEvent(viewLifecycleOwner, { interaction ->
            mySiteViewModel.onQuickStartMenuInteraction(interaction)
        })
        // todo: mySiteViewModel.onShowSwipeRefreshLayout.observeEvent(viewLifecycleOwner, { showSwipeToRefreshLayout(it) })
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun handleNavigationAction(action: SiteNavigationAction) = when (action) {
        is SiteNavigationAction.OpenActivityLog -> ActivityLauncher.viewActivityLogList(activity, action.site)
        is SiteNavigationAction.OpenBackup -> ActivityLauncher.viewBackupList(activity, action.site)
        is SiteNavigationAction.OpenScan -> ActivityLauncher.viewScan(activity, action.site)
        is SiteNavigationAction.OpenPlan -> ActivityLauncher.viewBlogPlans(activity, action.site)
        is SiteNavigationAction.OpenPosts -> ActivityLauncher.viewCurrentBlogPosts(requireActivity(), action.site)
        is SiteNavigationAction.OpenPages -> ActivityLauncher.viewCurrentBlogPages(requireActivity(), action.site)
        is SiteNavigationAction.OpenHomepage -> ActivityLauncher.editLandingPageForResult(
                this,
                action.site,
                action.homepageLocalId
        )
        is SiteNavigationAction.OpenAdmin -> ActivityLauncher.viewBlogAdmin(activity, action.site)
        is SiteNavigationAction.OpenPeople -> ActivityLauncher.viewCurrentBlogPeople(activity, action.site)
        is SiteNavigationAction.OpenSharing -> ActivityLauncher.viewBlogSharing(activity, action.site)
        is SiteNavigationAction.OpenSiteSettings -> ActivityLauncher.viewBlogSettingsForResult(activity, action.site)
        is SiteNavigationAction.OpenThemes -> ActivityLauncher.viewCurrentBlogThemes(activity, action.site)
        is SiteNavigationAction.OpenPlugins -> ActivityLauncher.viewPluginBrowser(activity, action.site)
        is SiteNavigationAction.OpenMedia -> ActivityLauncher.viewCurrentBlogMedia(activity, action.site)
        is SiteNavigationAction.OpenUnifiedComments -> ActivityLauncher.viewUnifiedComments(activity, action.site)
        is SiteNavigationAction.OpenStats -> ActivityLauncher.viewBlogStats(activity, action.site)
        is SiteNavigationAction.ConnectJetpackForStats ->
            ActivityLauncher.viewConnectJetpackForStats(activity, action.site)
        is SiteNavigationAction.StartWPComLoginForJetpackStats ->
            ActivityLauncher.loginForJetpackStats(this@MySiteMenuTabFragment)
        is SiteNavigationAction.OpenJetpackSettings ->
            ActivityLauncher.viewJetpackSecuritySettings(activity, action.site)
        is SiteNavigationAction.OpenStories -> ActivityLauncher.viewStories(activity, action.site, action.event)
        is SiteNavigationAction.AddNewStory ->
            ActivityLauncher.addNewStoryForResult(activity, action.site, action.source)
        is SiteNavigationAction.AddNewStoryWithMediaIds -> ActivityLauncher.addNewStoryWithMediaIdsForResult(
                activity,
                action.site,
                action.source,
                action.mediaIds.toLongArray()
        )
        is SiteNavigationAction.AddNewStoryWithMediaUris -> ActivityLauncher.addNewStoryWithMediaUrisForResult(
                activity,
                action.site,
                action.source,
                action.mediaUris.toTypedArray()
        )
        is SiteNavigationAction.OpenDomains -> ActivityLauncher.viewDomainsDashboardActivity(
                activity,
                action.site
        )
        is SiteNavigationAction.OpenDomainRegistration -> ActivityLauncher.viewDomainRegistrationActivityForResult(
                activity,
                action.site,
                CTA_DOMAIN_CREDIT_REDEMPTION
        )
        is SiteNavigationAction.AddNewSite -> SitePickerActivity.addSite(activity, action.hasAccessToken)
        is SiteNavigationAction.ShowQuickStartDialog -> showQuickStartDialog(
                action.title,
                action.message,
                action.positiveButtonLabel,
                action.negativeButtonLabel
        )
        is SiteNavigationAction.OpenQuickStartFullScreenDialog -> openQuickStartFullScreenDialog(action)
        is SiteNavigationAction.OpenDraftsPosts ->
            ActivityLauncher.viewCurrentBlogPostsOfType(requireActivity(), action.site, PostListType.DRAFTS)
        is SiteNavigationAction.OpenScheduledPosts ->
            ActivityLauncher.viewCurrentBlogPostsOfType(requireActivity(), action.site, PostListType.SCHEDULED)
        is SiteNavigationAction.OpenEditorToCreateNewPost ->
            ActivityLauncher.addNewPostForResult(
                    requireActivity(),
                    action.site,
                    false,
                    PagePostCreationSourcesDetail.POST_FROM_MY_SITE
            )
        // The below navigation is temporary and as such not utilizing the 'action.postId' in order to navigate to the
        // 'Edit Post' screen. Instead, it fallbacks to navigating to the 'Posts' screen and targeting a specific tab.
        is SiteNavigationAction.EditDraftPost ->
            ActivityLauncher.viewCurrentBlogPostsOfType(requireActivity(), action.site, PostListType.DRAFTS)
        is SiteNavigationAction.EditScheduledPost ->
            ActivityLauncher.viewCurrentBlogPostsOfType(requireActivity(), action.site, PostListType.SCHEDULED)
        is SiteNavigationAction.OpenTodaysStats ->
            ActivityLauncher.viewBlogStatsForTimeframe(requireActivity(), action.site, StatsTimeframe.DAY)
        else -> Unit
    }

    private fun openQuickStartFullScreenDialog(action: SiteNavigationAction.OpenQuickStartFullScreenDialog) {
        val bundle = QuickStartFullScreenDialogFragment.newBundle(action.type)
        Builder(requireContext())
                .setTitle(action.title)
                .setOnConfirmListener(this)
                .setOnDismissListener(this)
                .setContent(QuickStartFullScreenDialogFragment::class.java, bundle)
                .build()
                .show(requireActivity().supportFragmentManager, FullScreenDialogFragment.TAG)
    }

    override fun onResume() {
        super.onResume()
        mySiteViewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        activity?.let {
            if (!it.isChangingConfigurations) {
                mySiteViewModel.clearActiveQuickStartTask()
                mySiteViewModel.dismissQuickStartNotice()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.recyclerView?.layoutManager?.let {
            outState.putParcelable(KEY_LIST_STATE, it.onSaveInstanceState())
        }
        (binding?.recyclerView?.adapter as? MySiteAdapter)?.let {
            outState.putBundle(KEY_NESTED_LISTS_STATES, it.onSaveInstanceState())
        }
    }

    @Suppress("ReturnCount", "LongMethod", "ComplexMethod")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            return
        }
        when (requestCode) {
            RequestCodes.DO_LOGIN -> if (resultCode == Activity.RESULT_OK) {
                mySiteViewModel.handleSuccessfulLoginResult()
            }
            RequestCodes.DOMAIN_REGISTRATION -> if (resultCode == Activity.RESULT_OK) {
                mySiteViewModel.handleSuccessfulDomainRegistrationResult(data.getStringExtra(DomainRegistrationActivity.RESULT_REGISTERED_DOMAIN_EMAIL))
            }
            RequestCodes.LOGIN_EPILOGUE,
            RequestCodes.CREATE_SITE -> {
                mySiteViewModel.performFirstStepAfterSiteCreation(
                        data.getIntExtra(
                                SitePickerActivity.KEY_SITE_LOCAL_ID,
                                SelectedSiteRepository.UNAVAILABLE
                        )
                )
            }
            RequestCodes.EDIT_LANDING_PAGE -> {
                mySiteViewModel.checkAndStartQuickStart(
                        data.getIntExtra(
                                SitePickerActivity.KEY_SITE_LOCAL_ID,
                                SelectedSiteRepository.UNAVAILABLE
                        )
                )
            }
        }
    }

    private fun showQuickStartDialog(
        @StringRes title: Int,
        @StringRes message: Int,
        @StringRes positiveButtonLabel: Int,
        @StringRes negativeButtonLabel: Int
    ) {
        val tag = TAG_QUICK_START_DIALOG
        val quickStartPromptDialogFragment = QuickStartPromptDialogFragment()
        quickStartPromptDialogFragment.initialize(
                tag,
                getString(title),
                getString(message),
                getString(positiveButtonLabel),
                R.drawable.img_illustration_site_about_280dp,
                getString(negativeButtonLabel)
        )
        // todo: annmarie what is the parent here? Is it MySiteFragment? or the Activity?
        quickStartPromptDialogFragment.show(parentFragmentManager, tag)
        AnalyticsTracker.track(AnalyticsTracker.Stat.QUICK_START_REQUEST_VIEWED)
    }

    private fun MySiteMenuTabFragmentBinding.loadData(cardAndItems: List<MySiteCardAndItem>) {
        recyclerView.setVisible(true)
        (recyclerView.adapter as? MySiteAdapter)?.loadData(cardAndItems.filterNot(SiteInfoCard::class.java::isInstance))
    }

    private fun showSnackbar(holder: SnackbarMessageHolder) {
        activity?.let { parent ->
            snackbarSequencer.enqueue(
                    SnackbarItem(
                            info = Info(
                                    view = parent.findViewById(R.id.coordinator),
                                    textRes = holder.message,
                                    duration = holder.duration,
                                    isImportant = holder.isImportant
                            ),
                            action = holder.buttonTitle?.let {
                                Action(
                                        textRes = holder.buttonTitle,
                                        clickListener = { holder.buttonAction() }
                                )
                            },
                            dismissCallback = { _, event -> holder.onDismissAction(event) }
                    )
            )
        }
    }

    private fun onPositiveClicked() {
        mySiteViewModel.startQuickStart()
    }

    private fun onNegativeClicked() {
        mySiteViewModel.ignoreQuickStart()
    }

    override fun onConfirm(result: Bundle?) {
        val task = result?.getSerializable(QuickStartFullScreenDialogFragment.RESULT_TASK) as? QuickStartTask
        task?.let { mySiteViewModel.onQuickStartTaskCardClick(it) }
    }

    override fun onDismiss() {
        mySiteViewModel.onQuickStartFullScreenDialogDismiss()
    }

    override fun handleAction(action: MySiteAction) {
        when (action) {
            is MySiteAction.QuickStartPromptOnNegativeClick -> onNegativeClicked()
            is MySiteAction.QuickStartPromptOnPositiveClick -> onPositiveClicked()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val KEY_LIST_STATE = "key_list_state"
        private const val KEY_NESTED_LISTS_STATES = "key_nested_lists_states"
        private const val TAG_QUICK_START_DIALOG = "TAG_QUICK_START_DIALOG"
        fun newInstance() = MySiteMenuTabFragment()
    }
}
