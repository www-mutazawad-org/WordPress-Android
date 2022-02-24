package org.wordpress.android.ui.mysite.tabs

import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.wordpress.android.R
import org.wordpress.android.R.dimen
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.MySiteDashboardTabFragmentBinding
import org.wordpress.android.ui.mysite.MySiteCardAndItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards
import org.wordpress.android.ui.mysite.MySiteViewModel
import org.wordpress.android.ui.mysite.MySiteViewModel.State
import org.wordpress.android.ui.mysite.cards.dashboard.CardsAdapter
import org.wordpress.android.ui.mysite.cards.dashboard.CardsDecoration
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.util.setVisible
import javax.inject.Inject

class MySiteDashboardTabFragment : Fragment(R.layout.my_site_dashboard_tab_fragment) {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var uiHelpers: UiHelpers

    private lateinit var viewModel: MySiteTabViewModel
    private lateinit var mySiteViewModel: MySiteViewModel
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
        viewModel = ViewModelProvider(this, viewModelFactory).get(MySiteTabViewModel::class.java)
        mySiteViewModel = ViewModelProvider(this, viewModelFactory).get(MySiteViewModel::class.java)
    }

    private fun MySiteDashboardTabFragmentBinding.setupContentViews(savedInstanceState: Bundle?) {
        actionableEmptyView.button.setOnClickListener { mySiteViewModel.onAddSitePressed() }

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
        mySiteViewModel.uiModel.observe(viewLifecycleOwner, { uiModel ->
//            hideRefreshIndicatorIfNeeded()
            when (val state = uiModel.state) {
                is State.SiteSelected -> loadData(state.cardAndItems)
                is State.NoSites -> loadEmptyView(state.shouldShowImage)
            }
        })
    }

    private fun MySiteDashboardTabFragmentBinding.loadData(cardAndItems: List<MySiteCardAndItem>) {
        recyclerView.setVisible(true)
        actionableEmptyView.setVisible(false)
        mySiteViewModel.setActionableEmptyViewGone(actionableEmptyView.isVisible) {
            actionableEmptyView.setVisible(false)
        }

        (recyclerView.adapter as? CardsAdapter)?.update(
                cardAndItems.filterIsInstance<DashboardCards>().first().cards)
    }

    private fun MySiteDashboardTabFragmentBinding.loadEmptyView(shouldShowEmptyViewImage: Boolean) {
        recyclerView.setVisible(false)
        mySiteViewModel.setActionableEmptyViewVisible(actionableEmptyView.isVisible) {
            actionableEmptyView.setVisible(true)
            actionableEmptyView.image.setVisible(shouldShowEmptyViewImage)
        }
        actionableEmptyView.image.setVisible(shouldShowEmptyViewImage)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onResume() {
        super.onResume()
        mySiteViewModel.onResume()
    }

    companion object {
        private const val KEY_LIST_STATE = "key_list_state"
        private const val KEY_NESTED_LISTS_STATES = "key_nested_lists_states"
        fun newInstance() = MySiteDashboardTabFragment()
    }
}
