package org.wordpress.android.ui.mysite.tabs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.MySiteMenuTabFragmentBinding
import javax.inject.Inject

class MySiteMenuTabFragment : Fragment(R.layout.my_site_menu_tab_fragment) {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: MySiteTabViewModel
    private var binding: MySiteMenuTabFragmentBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDagger()
        initViewModel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MySiteMenuTabFragmentBinding.bind(view).apply {
        }
    }

    private fun initDagger() {
        (requireActivity().application as WordPress).component().inject(this)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(MySiteTabViewModel::class.java)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        fun newInstance() = MySiteMenuTabFragment()
    }
}
