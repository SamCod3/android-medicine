package com.samcod3.meditrack.di

import com.samcod3.meditrack.ui.screens.leaflet.LeafletViewModel
import com.samcod3.meditrack.ui.screens.scanner.ScannerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { ScannerViewModel() }
    viewModel { params -> LeafletViewModel(params.get(), get()) }
}
