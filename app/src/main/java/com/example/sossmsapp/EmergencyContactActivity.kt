package com.example.sossmsapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EmergencyContactActivity : AppCompatActivity() {

    private lateinit var contactManager: ContactManager
    private lateinit var adapter: ContactAdapter

    // Request Codes
    private val PICK_CONTACT_REQUEST = 1001
    private val PERMISSION_CONTACTS_REQUEST = 1002

    // Temporarily hold dialog references to update them from the picker
    private var activeNameEditText: EditText? = null
    private var activePhoneEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactManager = ContactManager(this)

        val isEditing = intent.getBooleanExtra("IS_EDITING", false)
        if (contactManager.getContacts().isNotEmpty() && !isEditing) {
            navigateToHome()
            return
        }

        setContentView(R.layout.activity_emergency_contact)

        val rvContacts = findViewById<RecyclerView>(R.id.rvContacts)
        rvContacts.layoutManager = LinearLayoutManager(this)

        adapter = ContactAdapter(contactManager.getContacts().toMutableList()) { contact ->
            showDeleteConfirmation(contact)
        }
        rvContacts.adapter = adapter

        findViewById<MaterialButton>(R.id.btnPolice).setOnClickListener { makeCall(EmergencyNumbers.POLICE) }
        findViewById<MaterialButton>(R.id.btnWomen).setOnClickListener { makeCall(EmergencyNumbers.WOMEN_HELPLINE) }
        findViewById<MaterialButton>(R.id.btnUnified).setOnClickListener { makeCall(EmergencyNumbers.EMERGENCY) }

        findViewById<FloatingActionButton>(R.id.btnAddContact).setOnClickListener {
            showAddContactDialog()
        }

        val btnSaveContinue = findViewById<MaterialButton>(R.id.btnSaveContinue)
        if (isEditing) btnSaveContinue.text = "Back to Home"

        btnSaveContinue.setOnClickListener {
            if (contactManager.getContacts().isNotEmpty()) {
                navigateToHome()
            } else {
                Toast.makeText(this, "Please add at least one guardian contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddContactDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)

        activeNameEditText = dialogView.findViewById(R.id.etName)
        activePhoneEditText = dialogView.findViewById(R.id.etPhone)
        val btnPick = dialogView.findViewById<ImageButton>(R.id.btnPickContact)

        btnPick?.setOnClickListener {
            checkPermissionAndPick()
        }

        builder.setView(dialogView)
            .setTitle("Add Guardian")
            .setPositiveButton("Add") { _, _ ->
                val name = activeNameEditText?.text.toString().trim()
                val phone = activePhoneEditText?.text.toString().trim()

                if (name.isNotEmpty() && validatePhone(phone)) {
                    val success = contactManager.addContact(EmergencyContact(name, phone))
                    if (success) {
                        refreshList()
                    } else {
                        Toast.makeText(this, "Limit reached or duplicate number", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Invalid name or phone", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissionAndPick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_CONTACTS_REQUEST)
        } else {
            launchContactPicker()
        }
    }

    @Suppress("DEPRECATION")
    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            val contactUri: Uri = data?.data ?: return
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    var number = cursor.getString(1)

                    // Clean number: remove spaces, dashes, and parentheses
                    number = number.replace(Regex("[\\s\\-\\(\\)]"), "")

                    activeNameEditText?.setText(name)
                    activePhoneEditText?.setText(number)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CONTACTS_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker()
        } else {
            Toast.makeText(this, "Permission required to pick contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(contact: EmergencyContact) {
        AlertDialog.Builder(this)
            .setTitle("Remove Guardian")
            .setMessage("Remove ${contact.name} from emergency list?")
            .setPositiveButton("Remove") { _, _ ->
                contactManager.removeContact(contact.phoneNumber)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validatePhone(phone: String): Boolean {
        return phone.length >= 10 && phone.all { it.isDigit() || it == '+' }
    }

    private fun refreshList() {
        adapter.updateData(contactManager.getContacts())
        // Tell the service to update its notification with the new contact count
        val serviceIntent = Intent(this, ShakeService::class.java)
        startService(serviceIntent)
    }

    private fun makeCall(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        startActivity(intent)
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    // --- RECYCLERVIEW ADAPTER ---
    inner class ContactAdapter(
        private var contacts: MutableList<EmergencyContact>,
        private val onRemoveClick: (EmergencyContact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.tvContactName)
            val phoneText: TextView = view.findViewById(R.id.tvContactPhone)
            val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return ContactViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val contact = contacts[position]
            holder.nameText.text = contact.name
            holder.phoneText.text = contact.phoneNumber
            holder.deleteBtn.setOnClickListener { onRemoveClick(contact) }
        }

        override fun getItemCount() = contacts.size

        fun updateData(newContacts: List<EmergencyContact>) {
            contacts.clear()
            contacts.addAll(newContacts)
            notifyDataSetChanged()
        }
    }
}