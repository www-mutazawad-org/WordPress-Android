package org.wordpress.android.ui.mysite.tabs

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MySiteTabsAdapter(
    parent: Fragment,
    private val tabs: List<String>
) : FragmentStateAdapter(parent) {
    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return if (position == 0 ) {
            MySiteMenuTabFragment.newInstance()
        } else {
            MySiteDashboardTabFragment.newInstance()
        }
    }
}
