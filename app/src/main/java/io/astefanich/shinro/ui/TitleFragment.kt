package io.astefanich.shinro.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import arrow.core.Option
import arrow.core.Some
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.Games
import com.google.android.gms.games.LeaderboardsClient
import io.astefanich.shinro.R
import io.astefanich.shinro.common.PlayRequest
import io.astefanich.shinro.databinding.FragmentTitleBinding
import timber.log.Timber
import javax.inject.Inject

class TitleFragment : Fragment() {

    companion object {
        const val SIGN_IN_REQUEST_CODE = 1001
        const val SIGN_IN_AND_SHOW_LEADERBOARDS = 1005
        const val SIGN_IN_AND_SHOW_ACHIEVEMENTS = 1006
        const val SHOW_LEADERBOARDS_REQUEST_CODE = 1002
        const val SHOW_ACHIEVEMENTS_REQUEST_CODE = 1003
    }

    @Inject
    @JvmSuppressWildcards
    lateinit var leaderboardsClient: Option<LeaderboardsClient>

    @Inject
    @JvmSuppressWildcards
    lateinit var achievementsClient: Option<AchievementsClient>

    @Inject
    @JvmSuppressWildcards
    lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var binding: FragmentTitleBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        (activity as MainActivity)
            .mainActivityComponent
            .getTitleComponent()
            .inject(this)

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_title, container, false)


        binding.playResumeChip.setOnClickListener {
            findNavController().navigate(
                TitleFragmentDirections.actionTitleToGame(PlayRequest.Resume)
            )
        }

        binding.howToPlayTipsChip.setOnClickListener {
            findNavController().navigate(
                TitleFragmentDirections.actionTitleToTipsChoice()
            )
        }

        binding.leaderboardChip.setOnClickListener {
            when (leaderboardsClient) {
                is Some -> {
                    showLeaderboard((leaderboardsClient as Some<LeaderboardsClient>).t)
                }
                else -> {
                    startActivityForResult(
                        googleSignInClient.signInIntent,
                        SIGN_IN_AND_SHOW_LEADERBOARDS
                    )
                }
            }
        }

        binding.achievementsChip.setOnClickListener {
            when (achievementsClient) {
                is Some -> {
                    showAchievements((achievementsClient as Some<AchievementsClient>).t)
                }
                else -> {
                    startActivityForResult(
                        googleSignInClient.signInIntent,
                        SIGN_IN_AND_SHOW_ACHIEVEMENTS
                    )
                }
            }
        }

        binding.statisticsChip.setOnClickListener {
            findNavController().navigate(
                TitleFragmentDirections.actionTitleToStatistics()
            )
        }

        binding.aboutChip.setOnClickListener {
            findNavController().navigate(TitleFragmentDirections.actionTitleToAbout())
        }

        binding.lifecycleOwner = this

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("onViewCreated")
        val silentSignInTask = googleSignInClient.silentSignIn()
        //this is redundant after first sign-in if user has auto sign-in (DI provides last signed in account)
        silentSignInTask.addOnSuccessListener { acct ->
            Timber.d("signed in silently...")
            leaderboardsClient = Some(Games.getLeaderboardsClient(requireActivity(), acct))
            achievementsClient = Some(Games.getAchievementsClient(requireActivity(), acct))
        }
        silentSignInTask.addOnFailureListener {
            Timber.d("failed to sign in silently")
        }
    }

    private fun showLeaderboard(client: LeaderboardsClient) {
        val leaderboardIntent = client.allLeaderboardsIntent
        leaderboardIntent.addOnSuccessListener { intent ->
            startActivityForResult(intent, TitleFragment.SHOW_LEADERBOARDS_REQUEST_CODE)
        }
        leaderboardIntent.addOnFailureListener { intent ->
            AlertDialog.Builder(requireActivity())
                .setMessage(R.string.signed_out)
                .setNeutralButton(android.R.string.ok, null).show()
        }

    }

    private fun showAchievements(client: AchievementsClient) {
        val achievementsClient = client.achievementsIntent
        achievementsClient.addOnSuccessListener { intent ->
            startActivityForResult(intent, TitleFragment.SHOW_ACHIEVEMENTS_REQUEST_CODE)
        }
        achievementsClient.addOnFailureListener { ex ->
            AlertDialog.Builder(requireActivity())
                .setMessage(R.string.signed_out)
                .setNeutralButton(android.R.string.ok, null).show()
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SIGN_IN_AND_SHOW_LEADERBOARDS || requestCode == SIGN_IN_AND_SHOW_ACHIEVEMENTS) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            result?.apply {
                if (isSuccess) {
                    val rawLeaderboardsClient = Games.getLeaderboardsClient(requireActivity(), signInAccount!!)
                    val rawAchievementsClient = Games.getAchievementsClient(requireActivity(), signInAccount!!)
                    leaderboardsClient = Some(rawLeaderboardsClient)
                    achievementsClient = Some(rawAchievementsClient)
                    if (requestCode == SIGN_IN_AND_SHOW_LEADERBOARDS)
                        showLeaderboard(rawLeaderboardsClient)
                    else
                        showAchievements(rawAchievementsClient)
                } else {
                    AlertDialog.Builder(requireActivity())
                        .setMessage(R.string.games_connection_failed)
                        .setNeutralButton(android.R.string.ok, null).show()
                }
            }
        }
    }

    //text reverts from bold to normal when popping/exiting from fragment
    override fun onStart() {
        super.onStart()
        if (this::binding.isInitialized) {
            Timber.i("title frag onStart")
            binding.playResumeChip.typeface = Typeface.DEFAULT_BOLD
            binding.howToPlayTipsChip.typeface = Typeface.DEFAULT_BOLD
            binding.statisticsChip.typeface = Typeface.DEFAULT_BOLD
            binding.leaderboardChip.typeface = Typeface.DEFAULT_BOLD
            binding.achievementsChip.typeface = Typeface.DEFAULT_BOLD
            binding.aboutChip.typeface = Typeface.DEFAULT_BOLD
        }
    }

}