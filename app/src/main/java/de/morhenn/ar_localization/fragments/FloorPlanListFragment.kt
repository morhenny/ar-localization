package de.morhenn.ar_localization.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.databinding.FragmentFloorPlanListBinding
import de.morhenn.ar_localization.model.FloorPlan
import de.morhenn.ar_localization.utils.FileLog
import de.morhenn.ar_localization.viewmodel.FloorPlanViewModel

class FloorPlanListFragment : Fragment() {

    //viewBinding
    private var _binding: FragmentFloorPlanListBinding? = null
    private val binding get() = _binding!!

    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentFloorPlanListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.floorPlanList.layoutManager = LinearLayoutManager(requireContext())
        binding.floorPlanList.adapter = viewModelFloorPlan.listAdapter

        binding.fabFloorPlanList.setOnClickListener {
            findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToAugmentedRealityFragment())
        }

    }
}