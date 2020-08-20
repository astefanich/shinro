package io.astefanich.shinro.ui


import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import androidx.core.view.get
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.NavigationUI
import io.astefanich.shinro.R
import io.astefanich.shinro.common.TimeSeconds
import io.astefanich.shinro.databinding.FragmentGameBinding
import io.astefanich.shinro.viewmodels.GameViewModel
import io.astefanich.shinro.viewmodels.MyEvent
import io.astefanich.shinro.viewmodels.ViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Named


class GameFragment : Fragment() {

    init {
        Timber.i("Game Fragment Init")
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    @field:Named("winBuzz")
    lateinit var winBuzzPattern: LongArray

    @Inject
    @field:Named("resetBuzz")
    lateinit var resetBuzzPattern: LongArray

    @Inject
    lateinit var toast: @JvmSuppressWildcards(true) (String) -> Unit

    @Inject
    lateinit var gameDialogBuilder: @JvmSuppressWildcards(true) (String, String, () -> Unit) -> AlertDialog.Builder

    @Inject
    lateinit var sharedPreferences: SharedPreferences


    val buzz = { pattern: LongArray -> Timber.i("buzzzzinggg ${Arrays.toString(pattern)}") }

    private var timerVisible = false
//    private lateinit var bus: EventBus
    private lateinit var uiTimePeriod: TimeSeconds
    private lateinit var viewModel: GameViewModel
    private lateinit var binding: FragmentGameBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        Timber.i("Game Fragment onCreateView")
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_game, container, false)
        val gameFragmentArgs by navArgs<GameFragmentArgs>()
        var playRequest = gameFragmentArgs.playRequest
        Timber.i("building new game component")
        (activity as MainActivity)
            .getMainActivityComponent()
            .getGameComponentBuilder()
            .playRequest(playRequest)
            .build()
            .inject(this)
        if (sharedPreferences.getBoolean("timer_visible", true)) {
            timerVisible = true
            binding.timeElapsed.visibility = View.VISIBLE
            uiTimePeriod = when (sharedPreferences.getString("timer_increment", "")) {
                "5 seconds" -> TimeSeconds.FIVE
                "10 seconds" -> TimeSeconds.TEN
                "30 seconds" -> TimeSeconds.THIRTY
                else -> TimeSeconds.ONE
            }
        }
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(GameViewModel::class.java)
        viewModel.gameEvent.observe(viewLifecycleOwner, Observer { handle(it) })
        initListeners()
        binding.vm = viewModel
        binding.lifecycleOwner = this
        setHasOptionsMenu(true)
        return binding.root
    }


    private fun handle(evt: GameViewModel.Event) {
        when (evt) {
            is GameViewModel.Event.TimeIncremented -> {
                if (timerVisible && (evt.sec % uiTimePeriod.seconds) == 0L) {
                    binding.timeElapsed.text = String.format(
                        resources.getString(R.string.timer_fmt),
                        DateUtils.formatElapsedTime(evt.sec)
                    )
                }
            }
            is GameViewModel.Event.IncorrectSolution -> toast("${evt.numIncorrect} of your marbles are wrong")
            is GameViewModel.Event.TooManyPlaced -> toast("You have placed ${evt.numPlaced} marbles, which is too many")
            is GameViewModel.Event.OutOfFreebies -> toast("Out of freebies")
            is GameViewModel.Event.FreebiePlaced -> {
                ObjectAnimator.ofArgb(
                    (binding.grid[evt.row] as ViewGroup)[evt.col],
                    "backgroundColor", Color.RED, resources.getColor(R.color.darkRed)
                )
                    .setDuration(3000)
                    .start()
            }
            is GameViewModel.Event.Reset -> {
                toast("Cleared")
                buzz(resetBuzzPattern)
            }
            is GameViewModel.Event.GameOver -> {
                viewModel.gameEvent.removeObservers(viewLifecycleOwner)
                var navDelay = 500L
                when (evt) {
                    is GameViewModel.Event.GameOver.Win -> {
                        toast("You Won!")
                        buzz(winBuzzPattern)
                        navDelay = 1500L
                    }
                }
                CoroutineScope(Dispatchers.Main).launch {
                    delay(navDelay)
                    findNavController().navigate(GameFragmentDirections.actionGameToGameSummary(evt.summary))
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.overflow_menu, menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings_destination -> {
                findNavController().navigate(GameFragmentDirections.actionGameToSettings())
                true
            }
            else -> {
                (NavigationUI.onNavDestinationSelected(item!!, requireView().findNavController())
                        || super.onOptionsItemSelected(item))
            }
        }
    }


    //onDestroy isn't reliably called. This call reliably saves active game
    override fun onStop() {
        super.onStop()
        Timber.i("ON STOP")
        viewModel.accept(GameViewModel.Command.PauseTimer)
        viewModel.accept(GameViewModel.Command.SaveGame)
    }

    override fun onStart() {
        super.onStart()
        Timber.i("ON START")
        EventBus.getDefault().register(this)
        viewModel.accept(GameViewModel.Command.ResumeTimer)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("ON RESUME")
    }

    //Associating views with command objects (issuing Command objects not possible via XML)
    private fun initListeners() {
        viewModel.isLoaded.observe(viewLifecycleOwner, Observer {
            if (it) {
                binding.progressBar.visibility = View.GONE
                binding.game.visibility = View.VISIBLE
            }
        })
        for (i in 1..8) {
            val row = binding.grid[i] as ViewGroup
            for (j in 1..8) {
                val cell = row[j]
                cell.setOnClickListener { viewModel.accept(GameViewModel.Command.Move(i, j)) }
            }
        }

        val gameIsActive = {
            when (viewModel.gameEvent.value) {
                is GameViewModel.Event.GameOver -> false
                else -> true
            }
        }

        binding.surrenderBoard.setOnClickListener {
            if (gameIsActive())
                gameDialogBuilder("Surrender", "Are you sure?") {
                    viewModel.accept(GameViewModel.Command.Surrender)
                }.show()
        }
        binding.resetBoard.setOnClickListener {
            if (gameIsActive())
                gameDialogBuilder("Reset", "Clear the board?\n(freebie will persist if used)") {
                    viewModel.accept(GameViewModel.Command.Reset)
                }.show()
        }
        binding.freebiesRemaining.setOnClickListener {
            if (gameIsActive())
                gameDialogBuilder(
                    "Freebie",
                    "Use freebie?\nThis will persist until the game is over"
                ) {
                    viewModel.accept(GameViewModel.Command.UseFreebie)
                }.show()
        }
        binding.setCheckpoint.setOnClickListener { viewModel.accept(GameViewModel.Command.SetCheckpoint) }
        binding.undoToCheckpoint.setOnClickListener { viewModel.accept(GameViewModel.Command.UndoToCheckpoint) }
        binding.undoButton.setOnClickListener { viewModel.accept(GameViewModel.Command.Undo) }
    }

    @Subscribe
    fun onMyEvent(evt: MyEvent) {
        Timber.i("I got the event: $evt")
    }
}


//    private fun buzz(pattern: LongArray) {
////        VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val effect = VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE)
//            Timber.i("buzzzing")
////                buzzer.vibrate(VibrationEffect.createWaveform(pattern, -1))
//        } else {
//            Timber.i("cant buzz yo")
////                buzzer.vibrate(pattern, -1) //deprecated in API 26
//        }
////        val buzzer = activity?.getSystemService<Vibrator>()
////        buzzer?.let {
////            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////                buzzer.vibrate(VibrationEffect.createWaveform(pattern, -1))
////            } else {
////                buzzer.vibrate(pattern, -1) //deprecated in API 26
////            }
////        }
//    }
