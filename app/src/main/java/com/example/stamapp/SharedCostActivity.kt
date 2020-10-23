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

class SharedCostActivity : AppCompatActivity() {

    /// variables and values ///
    private var existingNames: Array<String>? = null
    private var thisUserName: String? = null
    private var selectedUsers = ArrayList<String>()
    private var existingEvents: Array<String>? = null
    private var selectedEventIndex = -1
    private val resultIntent = Intent()
    /// widgets ///
    private var totalCostEditText: EditText? = null
    private var costPerPersonEditText: EditText? = null
    private var chooseSharersButton: Button? = null
    private var nSharersTextView: TextView? = null
    private var chooseEventButton: Button? = null
    private var chosenEventTextView: TextView? = null
    private var descriptionEditText: EditText? = null
    private var submitSharedCostButton: Button? = null
    private var cancelSharedCostButton: Button? = null
    private var sharersDialog: AlertDialog.Builder? = null
    private var eventDialog: AlertDialog.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_cost)

        // set widgets
        totalCostEditText = findViewById(R.id.totalCostEditText)
        costPerPersonEditText = findViewById(R.id.costPerPersonEditText)
        chooseSharersButton = findViewById(R.id.chooseSharersButton)
        nSharersTextView = findViewById(R.id.nSharersTextView)
        chooseEventButton = findViewById(R.id.chooseEventButton)
        chosenEventTextView = findViewById(R.id.chosenEventTextView)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        submitSharedCostButton = findViewById(R.id.submitSharedCostButton)
        cancelSharedCostButton = findViewById(R.id.cancelSharedCostButton)

        // get array of existing names from intent
        val nameslist = intent.getStringExtra("existingNames")?.split(";")
        if( nameslist==null ){ closeActivity() } // return without controlled RESULT_CANCELED
        if( nameslist!!.isEmpty() ){ closeActivity() } // return without controlled RESULT_CANCELED
        existingNames = Array(nameslist.size){""}
        for( i:Int in nameslist.indices ){ existingNames!![i] = nameslist[i] }
        thisUserName = intent.getStringExtra("userName" )
        
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
        
        // create dialog instances
        sharersDialog = AlertDialog.Builder( this )
        eventDialog = AlertDialog.Builder(this)
    }

    private fun readTotalCost(): Double {
        val rawText: String = totalCostEditText?.text.toString()
        // handle obvious case of empty text
        if( rawText.isEmpty() ){ return -1.0 }
        // handle general case
        if( !stringIsDouble(rawText) ){ return -1.0 }
        return rawText.toDouble()
    }

    private fun readCostPerPerson(): Double {
        val rawtext: String = costPerPersonEditText?.text.toString()
        // handle obvious case of empty text
        if( rawtext.isEmpty() ){ return -1.0 }
        // handle general case
        if( !stringIsDouble(rawtext) ){ return -1.0 }
        return rawtext.toDouble()
    }

    private fun setNSharers( nsharers: Int ){
        if( nsharers==1 ){ nSharersTextView!!.text = getString(R.string.nSharersTextView_onePersonText) }
        else{ nSharersTextView!!.text = getString(R.string.nSharersTextView_multiPersonText,nsharers.toString()) }
    }

    private fun setChosenEvent( chosenEvent: String ){
        chosenEventTextView!!.text = chosenEvent
    }

    private fun readDescription(): String{
        return descriptionEditText!!.text.toString()
    }

    // pop up window with existing users
    // (called by 'choose people to share' button)
    fun chooseSharers(@Suppress("UNUSED_PARAMETER")view: View){
        // set items for initial selection
        val initiallySelected = BooleanArray(existingNames!!.size){false}
        for( i: Int in existingNames!!.indices){
            if( existingNames!![i] in selectedUsers ){ initiallySelected[i] = true }
        }
        sharersDialog!!.setTitle(R.string.chooseSharersDialogTitle)
        sharersDialog!!.setMultiChoiceItems( existingNames!!, initiallySelected) {
                _, which, isSelected ->
            val thisName: String = existingNames!![which]
            if( isSelected ){ selectedUsers.add( thisName) }
            else{
                if( thisName in selectedUsers ){ selectedUsers.remove( thisName) }
            }
        }
        sharersDialog!!.setPositiveButton( getString(R.string.enter) ) {
                _, _ ->
            // set info text view
            setNSharers( selectedUsers.size )
        }
        sharersDialog!!.setNegativeButton( getString(R.string.cancel) ){
                _, _ ->
            // reset selected users
            selectedUsers = ArrayList()
            setNSharers( 0 )
        }
        sharersDialog!!.create()
        sharersDialog!!.show()
    }

    // pop up window with existing events
    // (called by 'choose event' button)
    fun chooseEvent(@Suppress("UNUSED_PARAMETER")view: View){
        eventDialog!!.setTitle( R.string.chooseEventDialogTitle )
        eventDialog!!.setSingleChoiceItems( existingEvents!!, -1 ) {
                _, which ->
            selectedEventIndex = which
        }
        eventDialog!!.setPositiveButton( getString(R.string.enter) ) {
                _, _ ->
            // set info text view
            setChosenEvent( existingEvents!![selectedEventIndex] )
        }
        eventDialog!!.setNegativeButton( getString(R.string.cancel) ){
                _, _ ->
            // reset selected index
            selectedEventIndex = -1
            setChosenEvent( getString(R.string.chosenEventTextView_text) )
        }
        eventDialog!!.create()
        eventDialog!!.show()
    }

    // check if all conditions are met and if so, return result to MainActivity
    // (called by 'enter' button)
    fun submitSharedCost(@Suppress("UNUSED_PARAMETER")view: View){
        // check if either total cost or cost per person are set (and not both)
        val totalcost: Double = readTotalCost()
        var costperperson: Double = readCostPerPerson()
        if( totalcost < 0.0 && costperperson < 0.0){
            Toast.makeText( applicationContext,
                R.string.error_noCost,
                Toast.LENGTH_LONG ).show()
            return
        }
        else if( totalcost > 0.0 && costperperson > 0.0){
            Toast.makeText( applicationContext,
                R.string.error_bothCosts,
                Toast.LENGTH_LONG ).show()
            return
        }
        // check number of sharers
        val nsharers = selectedUsers.size
        if( nsharers==0 ){
            Toast.makeText( applicationContext,
                R.string.error_noSharer,
                Toast.LENGTH_LONG ).show()
            return
        }
        // check chosen event
        val enteredEvent = readDescription()
        if( selectedEventIndex < 0 && enteredEvent.isEmpty() ){
            Toast.makeText( applicationContext,
                R.string.error_noEvent,
                Toast.LENGTH_LONG ).show()
            return
        }
        else if( selectedEventIndex > 0 && enteredEvent.isNotEmpty() ){
            Toast.makeText( applicationContext,
                R.string.error_bothEvents,
                Toast.LENGTH_LONG*2 ).show()
            return
        }
        val chosenEvent: String
        var isNewEvent = false
        if( selectedEventIndex>=0 ){ chosenEvent = existingEvents!![selectedEventIndex] }
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
        if(costperperson < 0.0){ costperperson = totalcost/nsharers }
        resultIntent.putExtra("costPerPerson", costperperson )
        resultIntent.putExtra("sharingUsers", selectedUsers )
        resultIntent.putExtra("eventName", chosenEvent )
        resultIntent.putExtra("isNewEvent", isNewEvent )
        setResult(Activity.RESULT_OK, resultIntent)
        closeActivity()
    }

    // cancel and close this activity
    // (called by 'cancel' button)
    fun cancelSharedCost(@Suppress("UNUSED_PARAMETER")view: View){
        setResult(Activity.RESULT_CANCELED,resultIntent)
        closeActivity()
    }

    private fun closeActivity(){
        super.onPause()
        finish()
    }
}