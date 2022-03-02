package org.wordpress.android.ui.mysite.tabs

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.wordpress.android.ui.utils.UiString
import java.lang.ref.WeakReference

class MySiteTabsAdapter(
    parent: Fragment,
    private val tabTitles: List<UiString>
) : FragmentStateAdapter(parent) {
    private val fragmentCache = mutableMapOf<Int, WeakReference<Fragment>>()

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        fragmentCache[position]?.get()?.let { return it }
        return getNewFragment(position)
                .also { fragmentCache[position] = WeakReference(it) }
    }

    private fun getNewFragment(position: Int) = if (position == 0) {
        MySiteTabFragment.newInstance(MySiteTabType.SITE_MENU)
    } else {
        MySiteTabFragment.newInstance(MySiteTabType.DASHBOARD)
    }

    fun getFragment(position: Int) = createFragment(position)
}
