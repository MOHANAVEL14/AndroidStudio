package com.example.sossmsapp.ai.security

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import com.example.sossmsapp.AppConstants
import com.example.sossmsapp.R

object PinVerificationDialog {

    /**
     * Shows a dialog asking for a PIN.
     * If correct, executes the [onCorrectPin] high-order function.
     */
    fun show(context: Context, onCorrectPin: () -> Unit) {
        val factory = LayoutInflater.from(context)
        val view = factory.inflate(R.layout.dialog_pin_verify, null)
        val pinEditText = view.findViewById<EditText>(R.id.etVerifyPin)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false) // Force the user to interact
            .setPositiveButton("Verify & Stop") { _, _ ->
                val inputPin = pinEditText.text.toString()
                val sharedPref = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                val savedPin = sharedPref.getString("APP_CANCEL_PIN", "1234")

                if (inputPin == savedPin) {
                    android.util.Log.d("PIN_DIALOG", "LOG_PIN_SUCCESS: Alert will be stopped")
                    onCorrectPin()
                } else {
                    android.util.Log.e("PIN_DIALOG", "LOG_PIN_FAILED: Wrong PIN entered")
                    Toast.makeText(context, "WRONG PIN! SOS Still Active", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Back") { d, _ ->
                d.dismiss()
            }
            .create()

        android.util.Log.d("PIN_DIALOG", "LOG_PIN_DIALOG_OPENED")
        dialog.show()
    }
}