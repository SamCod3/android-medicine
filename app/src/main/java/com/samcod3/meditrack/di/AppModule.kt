package com.samcod3.meditrack.di

import com.samcod3.meditrack.ui.screens.allreminders.AllRemindersViewModel
import com.samcod3.meditrack.ui.screens.home.HomeViewModel
import com.samcod3.meditrack.ui.screens.leaflet.LeafletViewModel
import com.samcod3.meditrack.ui.screens.profiles.ProfileViewModel
import com.samcod3.meditrack.ui.screens.reminders.ReminderViewModel
import com.samcod3.meditrack.ui.screens.scanner.ScannerViewModel
import com.samcod3.meditrack.ui.screens.search.SearchViewModel
import com.samcod3.meditrack.ui.screens.treatment.MyTreatmentViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { ScannerViewModel() }
    // LeafletViewModel needs: nationalCode, profileId, drugRepo, userMedRepo, reminderRepo, medDao
    // params[0] = nationalCode, params[1] = profileId
    viewModel { params -> 
        LeafletViewModel(
            nationalCode = params[0], 
            profileId = params[1], 
            drugRepository = get(), 
            userMedicationRepository = get(),
            reminderRepository = get(),
            medicationDao = get()
        ) 
    }
    viewModel { ProfileViewModel(get()) }
    viewModel { SearchViewModel(get()) }
    // HomeViewModel needs: profileId, userMedicationRepo
    viewModel { params -> 
        HomeViewModel(
            profileId = params[0],
            userMedicationRepository = get()
        )
    }
    // ReminderViewModel needs: medicationId, medicationName, reminderRepo
    viewModel { params ->
        ReminderViewModel(
            medicationId = params[0],
            medicationName = params[1],
            reminderRepository = get()
        )
    }
    // AllRemindersViewModel for global agenda view
    viewModel { AllRemindersViewModel(get()) }
    // MyTreatmentViewModel for treatment summary
    viewModel { MyTreatmentViewModel(get()) }
}
