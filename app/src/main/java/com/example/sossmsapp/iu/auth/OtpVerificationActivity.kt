package com.example.sossmsapp.iu.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.sossmsapp.databinding.ActivityOtpVerificationBinding
import com.example.sossmsapp.HomeActivity
import com.example.sossmsapp.data.FirebaseRepository
import com.example.sossmsapp.auth.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthCredential

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val firebaseRepo = FirebaseRepository()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
        val verificationId = intent.getStringExtra("VERIFICATION_ID")

        binding.tvSentTo.text = "Code sent to $phoneNumber"

        binding.btnVerify.setOnClickListener {
            val code = binding.etOtpCode.text.toString().trim()
            if (code.length == 6 && verificationId != null) {
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                signInWithPhoneAuthCredential(credential, phoneNumber)
            } else {
                Toast.makeText(this, "Enter valid 6-digit code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential, phone: String) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    user?.let {
                        // STEP 8: Create Firestore Profile
                        firebaseRepo.createUserProfile(it.uid, phone) { success ->
                            if (success) {
                                // STEP 9: Save Session
                                sessionManager.setLoggedIn(true)
                                navigateToHome()
                            } else {
                                Toast.makeText(this, "Profile Creation Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}