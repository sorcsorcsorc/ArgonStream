package com.argon.stream.ui.components

import androidx.lifecycle.ViewModel
import com.argon.stream.core.network.ConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConnectivityViewModel @Inject constructor(monitor: ConnectivityMonitor) : ViewModel() {
    val isOnline: StateFlow<Boolean> = monitor.isOnline
}
