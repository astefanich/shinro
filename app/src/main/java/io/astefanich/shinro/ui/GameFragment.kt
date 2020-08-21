package io.astefanich.shinro.ui


import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import androidx.core.view.get
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.NavigationUI
import arrow.core.Option
import arrow.core.Some
import io.astefanich.shinro.R
import io.astefanich.shinro.common.Difficulty
import io.astefanich.shinro.common.Grid
import io.astefanich.shinro.databinding.FragmentGameBinding
import io.astefanich.shinro.util.ShinroTimer
import io.astefanich.shinro.util.bindGridSvg
import io.astefanich.shinro.viewmodels.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject


class GameFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var toast: @JvmSuppressWildcards(true) (String) -> Unit

    @Inject
    lateinit var gameDialogBuilder: @JvmSuppressWildcards(true) (String, String, () -> Unit) -> AlertDialog.Builder

    @Inject
    lateinit var bus: EventBus

    @Inject
    lateinit var uiTimer: Option<ShinroTimer>

    private lateinit var viewModel: GameViewModel
    private lateinit var binding: FragmentGameBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        (activity as MainActivity)
            .mainActivityComponent
            .getGameComponent()
            .inject(this)
        bus.register(this)
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(GameViewModel::class.java)
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_game, container, false)
        for (i in 1..8) {
            val row = binding.grid[i] as ViewGroup
            for (j in 1..8) {
                val cell = row[j]
                cell.setOnClickListener { bus.post(MoveCommand(i, j)) }
            }
        }
        binding.apply {
            surrenderBoard.setOnClickListener {
                gameDialogBuilder(
                    "Surrender",
                    "Are you sure?"
                ) { bus.post(SurrenderCommand) }.show()
            }
            resetBoard.setOnClickListener {
                gameDialogBuilder(
                    "Reset",
                    "Clear the board?\n(freebie will persist if used)"
                ) { bus.post(ResetBoardCommand) }.show()
            }
            freebiesRemaining.setOnClickListener {
                gameDialogBuilder(
                    "Freebie",
                    "Use freebie?\nThis will persist until the game is over"
                ) { bus.post(UseFreebieCommand) }.show()
            }
            setCheckpoint.setOnClickListener { bus.post(SetCheckpointCommand) }
            undoToCheckpoint.setOnClickListener { bus.post(UndoToCheckpointCommand) }
            undoButton.setOnClickListener { bus.post(UndoCommand) }
            solveButton.setOnClickListener { bus.post(SolveBoardCommand) }
            lifecycleOwner = this@GameFragment
        }
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gameFragmentArgs by navArgs<GameFragmentArgs>()
        bus.post(LoadGameCommand(gameFragmentArgs.playRequest))
    }

    //onDestroy isn't reliably called? This call reliably saves active game
    override fun onStop() {
        super.onStop()
        bus.post(PauseTimerCommand)
        bus.post(SaveGameCommand)
        when (uiTimer) {
            is Some -> (uiTimer as Some<ShinroTimer>).t.pause()
        }
    }

    override fun onStart() {
        super.onStart()
        bus.post(ResumeTimerCommand)
        when (uiTimer) {
            is Some -> (uiTimer as Some<ShinroTimer>).t.resume()
        }
    }

    override fun onDestroy() {
        bus.unregister(this)
        super.onDestroy()
    }

    @Subscribe
    fun on(evt: GameLoadedEvent) {
        val drawable =
            if (evt.difficulty == Difficulty.EASY) R.drawable.ic_green_circle32
            else if (evt.difficulty == Difficulty.MEDIUM) R.drawable.ic_blue_square32
            else R.drawable.ic_gray_diamond32
        binding.apply {
            difficultyText.text = evt.difficulty.repr
            difficultyIcon.setImageDrawable(resources.getDrawable(drawable))
            freebiesRemaining.text = String.format(
                resources.getString(R.string.freebies_remaining_fmt),
                evt.freebiesRemaining
            )
            refreshGrid(evt.grid)
            progressBar.visibility = View.GONE
            game.visibility = View.VISIBLE
        }
        when (uiTimer) {
            is Some -> {
                var uiTime = evt.startTime
                val timer = (uiTimer as Some<ShinroTimer>)
                binding.timeElapsed.visibility = View.VISIBLE
                timer.t.start {
                    binding.timeElapsed.post {
                        binding.timeElapsed.text = String.format(
                            resources.getString(R.string.timer_fmt),
                            DateUtils.formatElapsedTime(uiTime)
                        )
                        uiTime += timer.t.period.seconds
                    }
                }
            }
        }
        bus.post(StartTimerCommand)
    }

    @Subscribe
    fun on(evt: CellChangedEvent) {
        ((binding.grid[evt.row] as ViewGroup)[evt.col] as SquareImageView).bindSvg(evt.newVal)
    }

    @Subscribe
    fun on(evt: TwelveMarblesPlacedEvent) {
        bus.post(CheckSolutionCommand)
    }

    @Subscribe
    fun on(evt: BoardResetEvent) {
        refreshGrid(evt.newBoard)
    }

    @Subscribe
    fun on(evt: RevertedToCheckpointEvent) {
        refreshGrid(evt.newBoard)
    }

    @Subscribe
    fun on(evt: UndoStackActivatedEvent) {
        binding.undoButton.apply {
            isClickable = true
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_undo_white, 0, 0)
            setTextColor(resources.getColor(R.color.white))
        }
    }

    @Subscribe
    fun on(evt: UndoStackDeactivatedEvent) {
        binding.undoButton.apply {
            isClickable = false
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_undo_gray, 0, 0)
            setTextColor(resources.getColor(R.color.lightGray))
        }
    }

    @Subscribe
    fun on(evt: CheckpointSetEvent) {
        binding.setCheckpoint.text = resources.getString(R.string.reset_checkpoint)
        binding.undoToCheckpoint.apply {
            isClickable = true
            setTextColor(resources.getColor(R.color.white))
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_flag_empty_white, 0, 0)
        }
    }

    @Subscribe
    fun on(evt: CheckpointResetEvent) {
        toast("Checkpoint Reset")
    }

    @Subscribe
    fun on(evt: CheckpointDeactivatedEvent) {
        binding.setCheckpoint.text = resources.getString(R.string.set_checkpoint)
        binding.undoToCheckpoint.apply {
            isClickable = false
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_flag_empty_gray, 0, 0)
            setTextColor(resources.getColor(R.color.lightGray))
        }
    }


    @Subscribe
    fun on(evt: FreebiePlacedEvent) {
        val targetCell = (binding.grid[evt.row] as ViewGroup)[evt.col] as SquareImageView
        ObjectAnimator.ofArgb(
            targetCell,
            "backgroundColor",
            Color.RED,
            resources.getColor(R.color.darkRed)
        )
            .setDuration(3000)
            .start()
        bindGridSvg(targetCell, "M")
    }


    @Subscribe
    fun on(evt: OutOfFreebiesEvent) {
        toast("Out of freebies")
    }

    @Subscribe
    fun on(evt: IncorrectSolutionEvent) {
        toast("${evt.numIncorrect} of your marbles are wrong")
    }

    @Subscribe
    fun on(evt: TooManyPlacedEvent) {
        toast("You have placed ${evt.numPlaced} marbles, which is too many")
    }

    @Subscribe
    fun on(evt: GameCompletedEvent) {
        var navDelay = 1000L
        if (evt.summary.isWin) {
            toast("You Won!")
            navDelay = 2000L
        }
        bus.post(PauseTimerCommand)
        when (uiTimer) {
            is Some -> (uiTimer as Some<ShinroTimer>).t.pause()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            delay(navDelay)
            withContext(Dispatchers.Main) {
                findNavController().navigate(GameFragmentDirections.actionGameToGameSummary(evt.summary))
            }
        }
    }

    private fun refreshGrid(grid: Grid) {
        for (i in 0..8) {
            val row = binding.grid[i] as ViewGroup
            for (j in 0..8) {
                val cell = row[j] as SquareImageView
                cell.bindSvg(grid[i][j].current)
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.overflow_menu, menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        return when (item.itemId) {
//            R.id.settings_destination -> {
//                findNavController().navigate(GameFragmentDirections.actionGameToSettings())
//                true
//            }
//            else -> {
        return (NavigationUI.onNavDestinationSelected(item!!, requireView().findNavController())
                || super.onOptionsItemSelected(item))
//            }
//        }
    }

}
