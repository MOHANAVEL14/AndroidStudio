package com.example.sossmsapp.data

import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()

    fun createUserProfile(userId: String, phoneNumber: String, onComplete: (Boolean) -> Unit) {
        val user = hashMapOf(
            "userId" to userId,
            "phoneNumber" to phoneNumber,
            "createdAt" to Date()
        )

        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}