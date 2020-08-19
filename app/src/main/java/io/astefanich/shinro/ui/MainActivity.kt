package io.astefanich.shinro.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import dagger.android.support.DaggerAppCompatActivity
import io.astefanich.shinro.R
import io.astefanich.shinro.ShinroApplication
import io.astefanich.shinro.di.DaggerAppComponent
import io.astefanich.shinro.di.activities.main.MainActivityComponent
import timber.log.Timber
import javax.inject.Inject

//import io.astefanich.shinro.di.DaggerAppComponent

class MainActivity : DaggerAppCompatActivity() {

//    @Inject
//    lateinit var appComponent: DaggerAppComponent
//
    val mainActivityComponent by lazy {
//    Timber.i(" main activity created")
    (application as ShinroApplication)
        .appComponent
        .getMainActivityComponentBuilder()
        .actitivtyContext(this)
        .build()
    }
//    val mainActivityComponent =
//        DaggerAppComponent
//            .builder(
//            .builder()
//            .
//            .builder()
//            .(this)
//            .build()

//    fun mainActivityComponent(): MainActivityComponent {
//        Timber.i("main activity component requested")
//        return (application as ShinroApplication)
//            .appComponent
//            .getMainActivityComponentBuilder()
//            .actitivtyContext(this)
//            .build()
//    }

//    override fun androidInjector():  = mainActivityComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.MODE_NIGHT_YES
    }


}