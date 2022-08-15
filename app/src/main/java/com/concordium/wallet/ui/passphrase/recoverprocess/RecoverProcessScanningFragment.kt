package com.concordium.wallet.ui.passphrase.recoverprocess

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.concordium.wallet.databinding.FragmentRecoverProcessScanningBinding

class RecoverProcessScanningFragment : RecoverProcessBaseFragment() {
    private var _binding: FragmentRecoverProcessScanningBinding? = null
    private val binding get() = _binding!!
    private lateinit var _password: String

    companion object {
        @JvmStatic
        fun newInstance(viewModel: RecoverProcessViewModel, password: String) = RecoverProcessScanningFragment().apply {
            arguments = Bundle().apply {
                putSerializable(RecoverProcessViewModel.RECOVER_PROCESS_DATA, viewModel)
            }
            _password = password
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecoverProcessScanningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.recoverIdentitiesAndAccounts(_password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
