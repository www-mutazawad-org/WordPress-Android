package org.wordpress.android.ui.mysite

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCrop.Options
import com.yalantis.ucrop.UCropActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.MySiteFragmentBinding
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.RequestCodes
import org.wordpress.android.ui.TextInputDialogFragment
import org.wordpress.android.ui.domains.DomainRegistrationActivity.Companion.RESULT_REGISTERED_DOMAIN_EMAIL
import org.wordpress.android.ui.main.SitePickerActivity
import org.wordpress.android.ui.main.WPMainActivity
import org.wordpress.android.ui.main.utils.MeGravatarLoader
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.SiteInfoCard
import org.wordpress.android.ui.mysite.MySiteViewModel.State
import org.wordpress.android.ui.mysite.SiteIconUploadHandler.ItemUploadedModel
import org.wordpress.android.ui.mysite.tabs.MySiteTabsAdapter
import org.wordpress.android.ui.photopicker.MediaPickerConstants
import org.wordpress.android.ui.photopicker.MediaPickerLauncher
import org.wordpress.android.ui.photopicker.PhotoPickerActivity.PhotoPickerMediaSource
import org.wordpress.android.ui.posts.BasicDialogViewModel
import org.wordpress.android.ui.posts.BasicDialogViewModel.BasicDialogModel
import org.wordpress.android.ui.uploads.UploadService
import org.wordpress.android.ui.uploads.UploadUtilsWrapper
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T.MAIN
import org.wordpress.android.util.AppLog.T.UTILS
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.NetworkUtils
import org.wordpress.android.util.UriWrapper
import org.wordpress.android.util.WPSwipeToRefreshHelper
import org.wordpress.android.util.config.MySiteDashboardTabsFeatureConfig
import org.wordpress.android.util.getColorFromAttribute
import org.wordpress.android.util.helpers.SwipeToRefreshHelper
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.util.image.ImageType.USER
import org.wordpress.android.viewmodel.observeEvent
import java.io.File
import javax.inject.Inject

@Suppress("TooManyFunctions")
class MySiteFragment : Fragment(R.layout.my_site_fragment),
        TextInputDialogFragment.Callback {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var mySiteDashboardTabsFeatureConfig: MySiteDashboardTabsFeatureConfig
    @Inject lateinit var buildConfigWrapper: BuildConfigWrapper
    @Inject lateinit var meGravatarLoader: MeGravatarLoader
    @Inject lateinit var mediaPickerLauncher: MediaPickerLauncher
    @Inject lateinit var uploadUtilsWrapper: UploadUtilsWrapper
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var uiHelpers: UiHelpers

    private lateinit var viewModel: MySiteViewModel
    private lateinit var dialogViewModel: BasicDialogViewModel
    private lateinit var swipeToRefreshHelper: SwipeToRefreshHelper

    private var binding: MySiteFragmentBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The following prevents the soft keyboard from leaving a white space when dismissed.
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        (requireActivity().application as WordPress).component().inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory).get(MySiteViewModel::class.java)
        dialogViewModel = ViewModelProvider(requireActivity(), viewModelFactory)
                .get(BasicDialogViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MySiteFragmentBinding.bind(view).apply {
            setupToolbar()
            updateTabs()
            setupContentViews()
            setupObservers()
            swipeToRefreshHelper.isRefreshing = true
            fakePTRDelay()
        }
    }

    // todo: annmarie - REMOVE ME!! this is for testing the PTR concept only
    private fun fakePTRDelay() {
        GlobalScope.launch {
            delay(3000)
            swipeToRefreshHelper.isRefreshing = false
        }
    }

    private fun MySiteFragmentBinding.updateTabs() {
        val tabTitles = if (shouldShowTabs()) {
            arrayOf("Menu", "Dashboard")
        } else {
            arrayOf("Menu")
        }

        val adapter = MySiteTabsAdapter(this@MySiteFragment, tabTitles)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        tabLayout.visibility = if (tabTitles.size > 1) View.VISIBLE else View.GONE
    }

    private fun shouldShowTabs() =
            mySiteDashboardTabsFeatureConfig.isEnabled() && buildConfigWrapper.isMySiteTabsEnabled

    private fun MySiteFragmentBinding.setupToolbar() {
        toolbarMain.let { toolbar ->
            toolbar.inflateMenu(R.menu.my_site_menu)
            toolbar.menu.findItem(R.id.me_item)?.let { meMenu ->
                meMenu.actionView.let { actionView ->
                    actionView.setOnClickListener { viewModel.onAvatarPressed() }
                    TooltipCompat.setTooltipText(actionView, meMenu.title)
                }
            }
        }

        val avatar = root.findViewById<ImageView>(R.id.avatar)

        appbarMain.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val maxOffset = appBarLayout.totalScrollRange
            val currentOffset = maxOffset + verticalOffset

            val percentage = ((currentOffset.toFloat() / maxOffset.toFloat()) * 100).toInt()
            avatar?.let { avatar ->
                val minSize = avatar.minimumHeight
                val maxSize = avatar.maxHeight
                val modifierPx = (minSize.toFloat() - maxSize.toFloat()) * (percentage.toFloat() / 100) * -1
                val modifierPercentage = modifierPx / minSize
                val newScale = 1 + modifierPercentage

                avatar.scaleX = newScale
                avatar.scaleY = newScale
            }
        })
    }

    private fun MySiteFragmentBinding.setupContentViews() {
        swipeToRefreshHelper = WPSwipeToRefreshHelper.buildSwipeToRefreshHelper(swipeRefreshLayout) {
            if (NetworkUtils.checkConnection(requireActivity())) {
                viewModel.refresh(isPullToRefresh = true)
                fakePTRDelay()
            } else {
                swipeToRefreshHelper.isRefreshing = false
            }
        }

        val layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(
                MySiteCardAndItemDecoration(
                        horizontalMargin = resources.getDimensionPixelSize(R.dimen.margin_extra_large),
                        verticalMargin = resources.getDimensionPixelSize(R.dimen.margin_medium)
                )
        )
        val adapter = MySiteAdapter(imageManager, uiHelpers)
        recyclerView.adapter = adapter
    }

    private fun MySiteFragmentBinding.setupObservers() {
        hideRefreshIndicatorIfNeeded()

        viewModel.uiModel.observe(viewLifecycleOwner, { uiModel ->
            loadGravatar(uiModel.accountAvatarUrl)
            when (val state = uiModel.state) {
                is State.SiteSelected -> loadData(state.cardAndItems)
            }
        })
        viewModel.onBasicDialogShown.observeEvent(viewLifecycleOwner, { model ->
            dialogViewModel.showDialog(requireActivity().supportFragmentManager,
                    BasicDialogModel(
                            model.tag,
                            getString(model.title),
                            getString(model.message),
                            getString(model.positiveButtonLabel),
                            model.negativeButtonLabel?.let { label -> getString(label) },
                            model.cancelButtonLabel?.let { label -> getString(label) }
                    ))
        })
        viewModel.onTextInputDialogShown.observeEvent(viewLifecycleOwner, { model ->
            val inputDialog = TextInputDialogFragment.newInstance(
                    getString(model.title),
                    model.initialText,
                    getString(model.hint),
                    model.isMultiline,
                    model.isInputEnabled,
                    model.callbackId
            )
            inputDialog.setTargetFragment(this@MySiteFragment, 0)
            inputDialog.show(parentFragmentManager, TextInputDialogFragment.TAG)
        })
        viewModel.onNavigation.observeEvent(viewLifecycleOwner, { handleNavigationAction(it) })
        viewModel.onMediaUpload.observeEvent(viewLifecycleOwner, { UploadService.uploadMedia(requireActivity(), it) })
        dialogViewModel.onInteraction.observeEvent(viewLifecycleOwner, { viewModel.onDialogInteraction(it) })
        viewModel.onUploadedItem.observeEvent(viewLifecycleOwner, { handleUploadedItem(it) })
        viewModel.onShowSwipeRefreshLayout.observeEvent(viewLifecycleOwner, { showSwipeToRefreshLayout(it) })
    }

    private fun MySiteFragmentBinding.loadData(cardAndItems: List<MySiteCardAndItem>) {
        (recyclerView.adapter as? MySiteAdapter)?.loadData(cardAndItems.filterIsInstance<SiteInfoCard>())
    }

    private fun MySiteFragmentBinding.loadGravatar(avatarUrl: String) =
            root.findViewById<ImageView>(R.id.avatar)?.let {
                meGravatarLoader.load(
                        false,
                        meGravatarLoader.constructGravatarUrl(avatarUrl),
                        null,
                        it,
                        USER,
                        null
                )
            }

    @Suppress("ComplexMethod", "LongMethod")
    private fun handleNavigationAction(action: SiteNavigationAction) = when (action) {
        is SiteNavigationAction.OpenMeScreen -> ActivityLauncher.viewMeActivityForResult(activity)
        is SiteNavigationAction.OpenSitePicker -> ActivityLauncher.showSitePickerForResult(activity, action.site)
        is SiteNavigationAction.OpenSite -> ActivityLauncher.viewCurrentSite(activity, action.site, true)
        is SiteNavigationAction.OpenMediaPicker ->
            mediaPickerLauncher.showSiteIconPicker(this@MySiteFragment, action.site)
        is SiteNavigationAction.OpenCropActivity -> startCropActivity(action.imageUri)

        else -> Unit
    }

    private fun handleUploadedItem(itemUploadedModel: ItemUploadedModel) = when (itemUploadedModel) {
        is ItemUploadedModel.PostUploaded -> {
            uploadUtilsWrapper.onPostUploadedSnackbarHandler(
                    activity,
                    requireActivity().findViewById(R.id.coordinator),
                    isError = true,
                    isFirstTimePublish = false,
                    post = itemUploadedModel.post,
                    errorMessage = itemUploadedModel.errorMessage,
                    site = itemUploadedModel.site
            )
        }
        is ItemUploadedModel.MediaUploaded -> {
            uploadUtilsWrapper.onMediaUploadedSnackbarHandler(
                    activity,
                    requireActivity().findViewById(R.id.coordinator),
                    isError = true,
                    mediaList = itemUploadedModel.media,
                    site = itemUploadedModel.site,
                    messageForUser = itemUploadedModel.errorMessage
            )
        }
    }

    private fun startCropActivity(imageUri: UriWrapper) {
        val context = activity ?: return
        val options = Options()
        options.setShowCropGrid(false)
        options.setStatusBarColor(context.getColorFromAttribute(android.R.attr.statusBarColor))
        options.setToolbarColor(context.getColorFromAttribute(R.attr.wpColorAppBar))
        options.setToolbarWidgetColor(context.getColorFromAttribute(R.attr.colorOnSurface))
        options.setAllowedGestures(UCropActivity.SCALE, UCropActivity.NONE, UCropActivity.NONE)
        options.setHideBottomControls(true)
        UCrop.of(imageUri.uri, Uri.fromFile(File(context.cacheDir, "cropped_for_site_icon.jpg")))
                .withAspectRatio(1f, 1f)
                .withOptions(options)
                .start(requireActivity(), this)
    }

    companion object {
        fun newInstance(): MySiteFragment {
            return MySiteFragment()
        }
    }

    @Suppress("ReturnCount", "LongMethod", "ComplexMethod")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) {
            return
        }
        // todo: annmarie - most of these actions are handled by the viewModel
        //  todo: if it's needed by a child tab, then perhaps we can turn it into a MySiteAction OR
        //   todo: pass along the onActivityResult?
        //    todo: ARE we sharing viewmodels?
        when (requestCode) {
            RequestCodes.DO_LOGIN -> if (resultCode == Activity.RESULT_OK) {
                viewModel.handleSuccessfulLoginResult()
            }
            RequestCodes.SITE_ICON_PICKER -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                when {
                    data.hasExtra(MediaPickerConstants.EXTRA_MEDIA_ID) -> {
                        val mediaId = data.getLongExtra(MediaPickerConstants.EXTRA_MEDIA_ID, 0)
                        viewModel.handleSelectedSiteIcon(mediaId)
                    }
                    data.hasExtra(MediaPickerConstants.EXTRA_MEDIA_URIS) -> {
                        val mediaUriStringsArray = data.getStringArrayExtra(
                                MediaPickerConstants.EXTRA_MEDIA_URIS
                        ) ?: return

                        val source = PhotoPickerMediaSource.fromString(
                                data.getStringExtra(MediaPickerConstants.EXTRA_MEDIA_SOURCE)
                        )
                        val iconUrl = mediaUriStringsArray.getOrNull(0) ?: return
                        viewModel.handleTakenSiteIcon(iconUrl, source)
                    }
                    else -> {
                        AppLog.e(
                                UTILS,
                                "Can't resolve picked or captured image"
                        )
                    }
                }
            }
            RequestCodes.STORIES_PHOTO_PICKER,
            RequestCodes.PHOTO_PICKER -> if (resultCode == Activity.RESULT_OK) {
                viewModel.handleStoriesPhotoPickerResult(data)
            }
            UCrop.REQUEST_CROP -> {
                if (resultCode == UCrop.RESULT_ERROR) {
                    AppLog.e(
                            MAIN,
                            "Image cropping failed!",
                            UCrop.getError(data)
                    )
                }
                viewModel.handleCropResult(UCrop.getOutput(data), resultCode == Activity.RESULT_OK)
            }
            RequestCodes.DOMAIN_REGISTRATION -> if (resultCode == Activity.RESULT_OK) {
                // todo: annmarie - this could have implications for both tab fragments
                viewModel.handleSuccessfulDomainRegistrationResult(data.getStringExtra(RESULT_REGISTERED_DOMAIN_EMAIL))
            }
            RequestCodes.LOGIN_EPILOGUE,
            RequestCodes.CREATE_SITE -> {
                viewModel.performFirstStepAfterSiteCreation(
                        data.getIntExtra(
                                SitePickerActivity.KEY_SITE_LOCAL_ID,
                                SelectedSiteRepository.UNAVAILABLE
                        )
                )
            }
            RequestCodes.SITE_PICKER -> {
                if (data.getIntExtra(WPMainActivity.ARG_CREATE_SITE, 0) == RequestCodes.CREATE_SITE) {
                    viewModel.performFirstStepAfterSiteCreation(
                            data.getIntExtra(
                                    SitePickerActivity.KEY_SITE_LOCAL_ID,
                                    SelectedSiteRepository.UNAVAILABLE
                            )
                    )
                }
            }
            RequestCodes.EDIT_LANDING_PAGE -> {
                viewModel.checkAndStartQuickStart(
                        data.getIntExtra(
                                SitePickerActivity.KEY_SITE_LOCAL_ID,
                                SelectedSiteRepository.UNAVAILABLE
                        )
                )
            }
        }
    }

    private fun showSwipeToRefreshLayout(isEnabled: Boolean) {
        swipeToRefreshHelper.setEnabled(isEnabled)
    }

    private fun hideRefreshIndicatorIfNeeded() {
        swipeToRefreshHelper.isRefreshing = viewModel.isRefreshing()
    }

    fun handleAction(action: MySiteAction) {
        // todo: iterate through all the child fragments and send the action to non-nulls who can handle it
        // todo: is this really necessary OR should we just go back to the listener?
        // todo: annmarie - need to pass this on to children fragment
        (requireActivity().supportFragmentManager.fragments)
                .filterIsInstance<MySiteActionHandler>()
                .forEach { _ -> handleAction(action) }
       }

    override fun onSuccessfulInput(input: String, callbackId: Int) {
        viewModel.onSiteNameChosen(input)
    }

    override fun onTextInputDialogDismissed(callbackId: Int) {
        viewModel.onSiteNameChooserDismissed()
    }
}

// TODO: Annmarie -- this may go by the wayside - leaving for the POC
sealed class MySiteAction {
    data class QuickStartPromptOnPositiveClick(val instanceTag: String) : MySiteAction()
    data class QuickStartPromptOnNegativeClick(val instanceTag: String) : MySiteAction()
}

// TODO: Annmarie -- this may go by the wayside - leaving for the POC
interface MySiteActionHandler {
    fun handleAction(action: MySiteAction)
}
