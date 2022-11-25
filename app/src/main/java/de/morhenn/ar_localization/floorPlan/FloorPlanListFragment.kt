package de.morhenn.ar_localization.floorPlan

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import de.morhenn.ar_localization.R
import de.morhenn.ar_localization.ar.ArState
import de.morhenn.ar_localization.databinding.DialogNewAnchorBinding
import de.morhenn.ar_localization.databinding.DialogNewFloorPlanBinding
import de.morhenn.ar_localization.databinding.FragmentFloorPlanListBinding

class FloorPlanListFragment : Fragment() {

    //viewBinding
    private var _binding: FragmentFloorPlanListBinding? = null
    private val binding get() = _binding!!

    private val viewModelFloorPlan: FloorPlanViewModel by navGraphViewModels(R.id.nav_graph_xml)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFloorPlanListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.floorPlanList.layoutManager = LinearLayoutManager(requireContext())
        binding.floorPlanList.adapter = viewModelFloorPlan.listAdapter

        binding.fabFloorPlanList.setOnClickListener {
            showDialogToCreate()
        }

    }

    private fun showDialogToCreate() {
        val dialogBinding = DialogNewFloorPlanBinding.inflate(LayoutInflater.from(requireContext()))
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogBinding.root)
        val dialog = builder.show()
        dialogBinding.dialogNewFloorPlanButtonConfirm.setOnClickListener {
            if (dialogBinding.dialogNewAnchorInputName.text.toString().isNotEmpty()) {
                viewModelFloorPlan.nameForNewFloorPlan = dialogBinding.dialogNewAnchorInputName.text.toString()
                viewModelFloorPlan.infoForNewFloorPlan = dialogBinding.dialogNewAnchorInputInfo.text.toString()
                dialog.dismiss()
                findNavController().navigate(FloorPlanListFragmentDirections.actionFloorPlanListFragmentToAugmentedRealityFragment())
            } else {
                dialogBinding.dialogNewFloorPlanInputNameLayout.error = getString(R.string.dialog_new_floor_plan_error)
            }
        }
        dialogBinding.dialogNewFloorPlanButtonCancel.setOnClickListener {
            dialog.cancel()
        }
    }
}