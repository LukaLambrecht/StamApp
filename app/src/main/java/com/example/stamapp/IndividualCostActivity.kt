package com.example.stamapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.example.stamapp.stamformat.stringIsDouble

class IndividualCostActivity : AppCompatActivity() {

    /// variables and values ///
    private var existingEvents: Array<String>? = null
    private var selectedIndex: Int = -1
    private val resultIntent = Intent()
    /// widgets ///
    private var costEditText: EditText? = null
    private var chosenEventTextView: TextView? = null
    private var descriptionEditText: EditText? = null
    private var chooseEventButton: Button? = null
    private var dialog: AlertDialog.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_individual_cost)

        // set widgets
        costEditText = findViewById(R.id.costEditText)
        chosenEventTextView = findViewById(R.id.chosenEventTextView)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        chooseEventButton = findViewById(R.id.chooseEventButton)

        // get array of existing events from intent
        val eventstring = intent.getStringExtra("existingEvents")?:""
        // special handling in case no events are present in the sheet: existingEvents is empty array
        if( eventstring=="" ){
            existingEvents = arrayOf()
            setChosenEvent( getString(R.string.chosenEventTextView_noEventsText) )
            chooseEventButton!!.isClickable = false
        }
        // normal case
        else {
            val eventlist = eventstring.split(";")
            if (eventlist.isEmpty()) { closeActivity() } // return without controlled RESULT_CANCELED
            existingEvents = Array(eventlist.size) { "" }
            // fill the list of existing events; reverse the order to put last event on top in the view
            for (i: Int in eventlist.indices) { existingEvents!![i] = eventlist[eventlist.size-1-i] }
        }
        dialog = AlertDialog.Builder( this )
    }

    private fun readCost(): Double {
        val rawtext: String = costEditText?.text.toString()
        // handle obvious case of empty text
        if( rawtext.isEmpty() ){ return -1.0 }
        // handle general case
        if( !stringIsDouble(rawtext) ){ return -1.0 }
        return rawtext.toDouble()
    }

    private fun setChosenEvent( chosenEvent: String ){
        chosenEventTextView!!.text = chosenEvent
    }

    private fun readDescription(): String{
        return descriptionEditText!!.text.toString()
    }

    // pop up window with existing events
    // (called by 'choose event' button)
    fun chooseEvent(@Suppress("UNUSED_PARAMETER")view: View){
        dialog!!.setTitle(R.string.chooseEventDialogTitle)
        dialog!!.setSingleChoiceItems( existingEvents!!, -1 ) {
                _, which ->
            selectedIndex = which
        }
        dialog!!.setPositiveButton( R.string.enter) {
                _, _ ->
            // set info text view
            setChosenEvent( existingEvents!![selectedIndex] )
        }
        dialog!!.setNegativeButton( R.string.cancel ){
                _, _ ->
            // reset selected index
            selectedIndex = -1
            setChosenEvent( getString(R.string.chosenEventTextView_text) )
        }
        dialog!!.create()
        dialog!!.show()
    }

    // check if all conditions are met and if so, return result to MainActivity
    // (called by 'enter' button)
    fun submitIndividualCost(@Suppress("UNUSED_PARAMETER")view: View){
        // check if a valid cost was given
        val cost: Double = readCost()
        if( cost < 0.0 ){
            Toast.makeText( applicationContext,
                R.string.error_invalidCost,
                Toast.LENGTH_LONG ).show()
            return
        }
        // check chosen event
        val enteredEvent = readDescription()
        if( selectedIndex < 0 && enteredEvent.isEmpty() ){
            Toast.makeText( applicationContext,
                R.string.error_noEvent,
                Toast.LENGTH_LONG ).show()
            return
        }
        else if( selectedIndex > 0 && enteredEvent.isNotEmpty() ){
            Toast.makeText( applicationContext,
                R.string.error_bothEvents,
                Toast.LENGTH_LONG*2 ).show()
            return
        }
        val chosenEvent: String
        var isNewEvent = false
        if( selectedIndex>=0 ){ chosenEvent = existingEvents!![selectedIndex] }
        else{
            if( enteredEvent in existingEvents!! ){
                Toast.makeText(
                    applicationContext,
                    R.string.error_existingEvent,
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            chosenEvent = enteredEvent
            isNewEvent = true
        }
        // all conditions are met, return result
        resultIntent.putExtra("cost", cost )
        resultIntent.putExtra("eventName", chosenEvent )
        resultIntent.putExtra("isNewEvent", isNewEvent )
        setResult(Activity.RESULT_OK, resultIntent)
        closeActivity()
    }

    // cancel and close this activity
    // (called by 'cancel' button)
    fun cancelIndividualCost(@Suppress("UNUSED_PARAMETER")view: View){
        setResult(Activity.RESULT_CANCELED,resultIntent)
        closeActivity()
    }

    private fun closeActivity(){
        super.onPause()
        finish()
    }
}