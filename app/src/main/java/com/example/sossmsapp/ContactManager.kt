package com.example.sossmsapp
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContactManager(context: Context) {
    private val prefs = context.getSharedPreferences("EmergencyContacts", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getContacts(): MutableList<EmergencyContact> {
        val json = prefs.getString("contact_list", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<EmergencyContact>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val json = gson.toJson(contacts)
        prefs.edit().putString("contact_list", json).apply()
    }

    fun addContact(contact: EmergencyContact): Boolean {
        val list = getContacts()
        if (list.size >= 5) return false
        if (list.any { it.phoneNumber == contact.phoneNumber }) return false
        list.add(contact)
        saveContacts(list)
        return true
    }

    fun removeContact(phoneNumber: String) {
        val list = getContacts()
        list.removeAll { it.phoneNumber == phoneNumber }
        saveContacts(list)
    }
}