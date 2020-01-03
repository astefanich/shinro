package io.astefanich.shinro.ui


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController

import io.astefanich.shinro.R
import io.astefanich.shinro.databinding.TipsFragmentBinding
import io.astefanich.shinro.domain.InstructionType

class TipsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val binding: TipsFragmentBinding =
            DataBindingUtil.inflate(inflater, R.layout.tips_fragment, container, false)

        binding.generalRulesCard.setOnClickListener {
            findNavController().navigate(
                TipsFragmentDirections.actionTipsDestinationToInstructionsDestination(InstructionType.GENERAL)
            )
        }

        binding.pathfinderCard.setOnClickListener {
            findNavController().navigate(
                TipsFragmentDirections.actionTipsDestinationToInstructionsDestination(InstructionType.PATHFINDER)
            )
        }

        binding.blockerCard.setOnClickListener {
            findNavController().navigate(
                TipsFragmentDirections.actionTipsDestinationToInstructionsDestination(InstructionType.BLOCKER)
            )
        }
        binding.pigeonholeCard.setOnClickListener {
            findNavController().navigate(
                TipsFragmentDirections.actionTipsDestinationToInstructionsDestination(InstructionType.PIGEONHOLE)
            )
        }
        return binding.root
    }


}
