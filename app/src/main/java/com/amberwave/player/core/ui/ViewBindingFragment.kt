package com.amberwave.player.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 页面基类：ViewBinding 只在视图生命周期内有效（onDestroyView 后立即释放），
 * 并提供按视图 STARTED 生命周期收集状态流的统一写法。
 */
abstract class ViewBindingFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {
    private var _binding: VB? = null

    /** 仅在 onViewCreated 与 onDestroyView 之间访问。 */
    protected val binding: VB
        get() = checkNotNull(_binding) { "Binding accessed outside of the view lifecycle" }

    protected val isViewAvailable: Boolean
        get() = _binding != null

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflate(inflater, container, false).also { _binding = it }.root

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    protected fun <T> Flow<T>.collectWhileStarted(action: suspend (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { collect(action) }
        }
    }

    protected fun toast(@StringRes message: Int) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    protected fun toast(message: CharSequence?) {
        if (!message.isNullOrBlank()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
