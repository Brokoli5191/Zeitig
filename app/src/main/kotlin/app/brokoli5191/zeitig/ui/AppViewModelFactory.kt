package app.brokoli5191.zeitig.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.brokoli5191.zeitig.AppContainer
import app.brokoli5191.zeitig.ZeitigApplication

/** Resolves the [AppContainer] from the Application inside a ViewModel factory. */
fun CreationExtras.container(): AppContainer {
    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ZeitigApplication
    return application.container
}

inline fun <reified VM : ViewModel> factory(crossinline create: CreationExtras.(AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create(container()) } }
