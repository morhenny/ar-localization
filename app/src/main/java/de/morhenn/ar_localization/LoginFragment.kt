package de.morhenn.ar_localization

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import de.morhenn.ar_localization.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    companion object {
        private const val TAG = "LoginFragment"
    }

    //viewBinding
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth

    private lateinit var oneTapClient: SignInClient

    private val resultLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    Log.d(TAG, "No ID token")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed", e)
            }
        } else {
            Log.e(TAG, "Google sign in failed")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth

        oneTapClient = Identity.getSignInClient(requireActivity())
        val signInRequest = getGoogleSignInRequest()

        //make links in textview clickable
        binding.termsOfUseArcore.movementMethod = LinkMovementMethod.getInstance()
        binding.termsOfUseCloudAnchor.movementMethod = LinkMovementMethod.getInstance()

        binding.googleSignInButton.setOnClickListener {
            oneTapClient.beginSignIn(signInRequest)
                .addOnSuccessListener { result ->
                    try {
                        val intentSender = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                        resultLauncher.launch(intentSender)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e(TAG, "Error launching sign in intent", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "beginSignIn failed", e)
                    Toast.makeText(requireContext(), "Error signing in: $e", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToFloorPlanListFragment())
                } else {
                    Log.e(TAG, "signInWithCredential:failure", it.exception)
                }
            }
    }

    private fun getGoogleSignInRequest(): BeginSignInRequest {
        return BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.firebase_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            ).build()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in, skipping login and navigating to floorPlanList")
            findNavController().navigate(LoginFragmentDirections.actionLoginFragmentToFloorPlanListFragment())
        }
    }
}