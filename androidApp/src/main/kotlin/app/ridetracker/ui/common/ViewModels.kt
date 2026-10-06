package app.ridetracker.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import app.ridetracker.AppContainer
import app.ridetracker.RideTrackerApplication

val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as RideTrackerApplication).container
