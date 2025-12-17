package com.samcod3.meditrack.di

import com.samcod3.meditrack.ui.screens.leaflet.LeafletViewModel
import com.samcod3.meditrack.ui.screens.profiles.ProfileViewModel
import com.samcod3.meditrack.ui.screens.scanner.ScannerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { ScannerViewModel() }
    // LeafletViewModel needs: nationalCode, profileId, drugRepo, userMedRepo
    // params[0] = nationalCode, params[1] = profileId
    viewModel { params -> 
        LeafletViewModel(
            nationalCode = params[0], 
            profileId = params[1], 
            drugRepository = get(), 
            userMedicationRepository = get()
        ) 
    }
    viewModel { ProfileViewModel(get()) }
}
