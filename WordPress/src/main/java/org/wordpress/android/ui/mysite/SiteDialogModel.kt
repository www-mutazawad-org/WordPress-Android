package org.wordpress.android.ui.mysite

import androidx.annotation.StringRes
import org.wordpress.android.R
import org.wordpress.android.ui.mysite.tabs.MySiteTabViewModel

sealed class SiteDialogModel(
    val tag: String,
    @StringRes val title: Int,
    @StringRes open val message: Int,
    @StringRes val positiveButtonLabel: Int,
    @StringRes val negativeButtonLabel: Int? = null,
    @StringRes val cancelButtonLabel: Int? = null
) {
    // todo: annmarie - MSD tabs change
    object AddSiteIconDialogModel : SiteDialogModel(
            MySiteTabViewModel.TAG_ADD_SITE_ICON_DIALOG,
            R.string.my_site_icon_dialog_title,
            R.string.my_site_icon_dialog_add_message,
            R.string.yes,
            R.string.no,
            null
    )

    object ChangeSiteIconDialogModel : SiteDialogModel(
            MySiteTabViewModel.TAG_CHANGE_SITE_ICON_DIALOG,
            R.string.my_site_icon_dialog_title,
            R.string.my_site_icon_dialog_change_message,
            R.string.my_site_icon_dialog_change_button,
            R.string.my_site_icon_dialog_remove_button,
            R.string.my_site_icon_dialog_cancel_button
    )

    object ShowRemoveNextStepsDialog : SiteDialogModel(
            MySiteTabViewModel.TAG_REMOVE_NEXT_STEPS_DIALOG,
            R.string.quick_start_dialog_remove_next_steps_title,
            R.string.quick_start_dialog_remove_next_steps_message,
            R.string.remove,
            R.string.cancel
    )
}
