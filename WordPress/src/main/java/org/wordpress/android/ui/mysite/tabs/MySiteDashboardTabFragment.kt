package org.wordpress.android.ui.mysite.tabs

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.wordpress.android.R
import org.wordpress.android.R.dimen
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.MySiteDashboardTabFragmentBinding
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.PagePostCreationSourcesDetail
import org.wordpress.android.ui.mysite.MySiteCardAndItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards
import org.wordpress.android.ui.mysite.SiteNavigationAction
import org.wordpress.android.ui.mysite.cards.dashboard.CardsAdapter
import org.wordpress.android.ui.mysite.cards.dashboard.CardsDecoration
import org.wordpress.android.ui.mysite.tabs.MySiteDashboardTabViewModel.State
import org.wordpress.android.ui.posts.PostListType
import org.wordpress.android.ui.stats.StatsTimeframe
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.util.setVisible
import org.wordpress.android.viewmodel.observeEvent
import javax.inject.Inject

class MySiteDashboardTabFragment : Fragment(R.layout.my_site_dashboard_tab_fragment) {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var uiHelpers: UiHelpers

    private lateinit var viewModel: MySiteDashboardTabViewModel
    private var binding: MySiteDashboardTabFragmentBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDagger()
        initViewModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MySiteDashboardTabFragmentBinding.bind(view).apply {
            setupContentViews(savedInstanceState)
            setupObservers()
        }
    }

    private fun initDagger() {
        (requireActivity().application as WordPress).component().inject(this)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(MySiteDashboardTabViewModel::class.java)
    }

    private fun MySiteDashboardTabFragmentBinding.setupContentViews(savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity)

        savedInstanceState?.getParcelable<Parcelable>(KEY_LIST_STATE)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(CardsDecoration(resources.getDimensionPixelSize(dimen.margin_extra_large)))

        val adapter = CardsAdapter(imageManager, uiHelpers)

//       savedInstanceState?.getBundle(KEY_NESTED_LISTS_STATES)?.let {
//            adapter.onRestoreInstanceState(it)
//        }

        recyclerView.adapter = adapter
    }

    @Suppress("LongMethod")
    private fun MySiteDashboardTabFragmentBinding.setupObservers() {
        viewModel.uiModel.observe(viewLifecycleOwner, { uiModel ->
//            hideRefreshIndicatorIfNeeded()
            when (val state = uiModel.state) {
                is State.SiteSelected -> loadData(state.cardAndItems)
            }
        })
        viewModel.onNavigation.observeEvent(viewLifecycleOwner, { handleNavigationAction(it) })
    }

    private fun MySiteDashboardTabFragmentBinding.loadData(cardAndItems: List<MySiteCardAndItem>) {
        recyclerView.setVisible(true)
        (recyclerView.adapter as? CardsAdapter)?.update(
                cardAndItems.filterIsInstance<DashboardCards>().first().cards)
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun handleNavigationAction(action: SiteNavigationAction) = when (action) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    companion object {
        private const val KEY_LIST_STATE = "key_list_state"
        private const val KEY_NESTED_LISTS_STATES = "key_nested_lists_states"
        fun newInstance() = MySiteDashboardTabFragment()
    }
}
