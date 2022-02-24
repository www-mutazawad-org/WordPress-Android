package org.wordpress.android.ui.mysite

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.util.BuildConfigWrapper
import org.wordpress.android.util.config.MySiteDashboardTabsFeatureConfig
import javax.inject.Inject

// TODO: Annmarie - is this nothing more than a traffic cop - moving this to/fro
// Perhaps just a frame layout - that loads the proper fragment - keep track of it so it doesn't load
// over and over again
@Deprecated("Class is being deprecated - use MySiteTabbedFragment or MySiteNonTabbedFragment")
@Suppress("TooManyFunctions")
class MySiteFragment : Fragment(R.layout.my_site_fragment) {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var mySiteDashboardTabsFeatureConfig: MySiteDashboardTabsFeatureConfig
    @Inject lateinit var buildConfigWrapper: BuildConfigWrapper

    // TODO: Annmarie - think - is this really just a pass through class
    // TODO: Annmarie - If we start adding logic here, it defeats the purpose of separating
    // TODO: Annmarie - But it does keep logic out of WPMainActivity/WPMainNavigationView ++
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initDagger()

        var fragment = requireActivity().supportFragmentManager.findFragmentByTag(TAG_MY_SITE_FRAGMENT)

        if (fragment == null) {
            fragment = if (shouldShowTabs())
                MySiteTabbedFragment.newInstance() else MySiteNonTabbedFragment.newInstance()
            requireActivity().supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment, TAG_MY_SITE_FRAGMENT)
                    .commit()
        }
    }

    private fun initDagger() {
        (requireActivity().application as WordPress).component().inject(this)
    }

    private fun shouldShowTabs() =
            mySiteDashboardTabsFeatureConfig.isEnabled() && buildConfigWrapper.isMySiteTabsEnabled

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return // no need to go any further
        requireActivity().supportFragmentManager.findFragmentByTag(TAG_MY_SITE_FRAGMENT)
                ?.onActivityResult(requestCode, resultCode, data)
    }

    // TODO: Annmarie - pass any actions on to any fragment that can handle them
    fun handleAction(action: MySiteAction) {
        (requireActivity().supportFragmentManager.findFragmentByTag(TAG_MY_SITE_FRAGMENT)
                as? MySiteActionHandler)?.handleAction(action)
    }

    companion object {
        @JvmStatic
        fun newInstance(): MySiteFragment {
            return MySiteFragment()
        }

        private const val TAG_MY_SITE_FRAGMENT = "tag_my_site_fragment"
    }
}

// TODO: Annmarie -- every action that can happen is represented as an MySiteAction - keeps things very clean
// TODO: Annmarie -- obviously this would move to a separate file
sealed class MySiteAction {
    data class QuickStartPromptOnPositiveClick(val instanceTag: String) : MySiteAction()
    data class QuickStartPromptOnNegativeClick(val instanceTag: String) : MySiteAction()
}

// TODO: Annmarie -- implemented by tabbed or non-tabbed instances of MySite fragments
// TODO: Annmarie -- not 100% in favor of this because your when clause needs to be exhaustive - shall see
interface MySiteActionHandler {
    fun handleAction(action: MySiteAction)
}
