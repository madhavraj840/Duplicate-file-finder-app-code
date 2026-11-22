package com.bunkwise.duplicatefilefinder.presentation.cleanup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.bunkwise.duplicatefilefinder.MainActivity
import com.bunkwise.duplicatefilefinder.R
import com.bunkwise.duplicatefilefinder.appContainer
import com.bunkwise.duplicatefilefinder.presentation.shared.SharedSelectionViewModel
import com.bunkwise.duplicatefilefinder.ui.Tab
import com.bunkwise.duplicatefilefinder.ui.tabScreen
import com.bunkwise.duplicatefilefinder.ads.ProFeature
import com.bunkwise.duplicatefilefinder.ads.Unlocks

/** The Duplicates tab: the last scan's groups, selection and the path to Review. */
class CleanupFragment : Fragment() {

    private val selection: SharedSelectionViewModel by activityViewModels {
        requireContext().appContainer.viewModelFactory
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        // With duplicates to go through, the page gets the whole screen: no nav, a back arrow instead.
        tabScreen(Tab.CLEANUP, showNav = { (selection.state.collectAsStateWithLifecycle().value.result?.groupCount ?: 0) == 0 }) {
            val state by selection.state.collectAsStateWithLifecycle()
            CleanupScreen(
                state = state,
                onToggleGroup = selection::toggleGroup,
                onOpenGroup = ::openGroup,
                onCategory = selection::setCategory,
                onSort = selection::setSort,
                onKeepRule = selection::setKeepRule,
                onQuery = selection::setSearchQuery,
                onSelectAll = ::onSelectAll,
                onReview = {
                    findNavController().navigate(R.id.reviewDeleteFragment, null, navOptions { launchSingleTop = true })
                },
                onGoScan = { (activity as? MainActivity)?.selectTab(R.id.homeFragment) },
                onBack = { (activity as? MainActivity)?.selectTab(R.id.homeFragment) },
                onPremium = { findNavController().navigate(R.id.premiumFragment) },
            )
        }

    private fun onSelectAll() {
        val state = selection.state.value
        when {
            state.markedFiles > 0 -> selection.clearSelection()
            Unlocks.unlocked(state.isPremium, ProFeature.SELECT_ALL) -> selection.selectAllVisible()
            else -> (activity as? MainActivity)?.requestUnlock(ProFeature.SELECT_ALL)
        }
    }

    private fun openGroup(groupId: Long) {
        selection.previewGroupId = groupId
        findNavController().navigate(R.id.previewFragment, null, navOptions { launchSingleTop = true })
    }
}
