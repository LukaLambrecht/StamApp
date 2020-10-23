package com.example.stamapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class LoginActivity : AppCompatActivity() {

    private var loginButton: Button? = null
    private var chooseUserNameTextView: TextView? = null
    private var chooseUserNameEditText: EditText? = null
    private var messageTextView: TextView? = null
    private var existingNames: List<String>? = null
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        loginButton = findViewById(R.id.loginButton)
        chooseUserNameTextView = findViewById(R.id.chooseUserNameTextView)
        chooseUserNameEditText = findViewById(R.id.chooseUserNameEditText)
        messageTextView = findViewById(R.id.messageTextView)
        existingNames = intent.getStringExtra("existingNames")?.split(";")

        chooseUserNameEditText?.addTextChangedListener(textWatcher)
    }

    // TextWatcher object, needed to clear message box when text has changed
    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            // no special behaviour needed here
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // no special behaviour needed here
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            clearMessage()
        }
    }

    // function to read current username from text edit widget
    private fun readUserName(): String {
        return chooseUserNameEditText?.text.toString()
    }

    // function to check if user-entered username is valid
    private fun userNameIsValid(userName: String): Boolean {
        // user names cannot be empty or blank
        if( userName.isEmpty() || userName.isBlank() ){ return false }
        // user names cannot contain ';' since this is used to concatenate and split list of names to one string
        if( userName.contains(";")){ return false }
        // more general check: get ascii value of each character and check if it is within acceptable ranges
        for( substr: Char in userName ){
            val asciicode = substr.toInt()
            if( !( (asciicode in 65..90) // capital letters
                     || (asciicode in 97..122) // small letters
                     || (asciicode in 48..57) // numbers
                     || (asciicode==32) )){ // space character
                return false
            }
        }
        return true
    }

    // function to check if user-entered username already exists
    private fun userNameExists(userName: String, existingNames: List<String>?): Boolean {
        if(existingNames==null){ return false }
        for( name in existingNames ){
            if( name==userName ){ return true }
        }
        return false
    }

    // function to clear the message box,
    // called when the edit text widget is clicked
    fun clearMessage(){
        messageTextView?.text = ""
        messageTextView?.setTextColor(Color.BLACK)
    }

    // main function of this activity,
    // called when clicking the login button
    fun attemptLogin(@Suppress("UNUSED_PARAMETER")view: View){
        // first read current user name from widget
        val userName = readUserName()
        // handle case where user name is invalid
        if( !userNameIsValid(userName) ){
            messageTextView?.setTextColor(Color.RED)
            messageTextView?.text = getString(R.string.invalid_chars)
            return
        }
        // handle case where user name already exists
        if( userNameExists(userName,existingNames) ){
            messageTextView?.setTextColor(Color.RED)
            messageTextView?.text = getString(R.string.invalid_exists)
            return
        }
        // user name is a good one; return it and close this activity
        resultIntent.putExtra("userName", userName)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}