package com.example.stamapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class ManualOrderActivity : AppCompatActivity() {

    private var availableItems: Array<String>? = null
    private var availableItemsToShow: Array<String>? = null
    private var selectedIndex: Int = -1
    private var dialog: AlertDialog.Builder? = null
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_order)

        // get array of available items from intent
        // every element in the array is supposed to be of the form <item>:<price>
        val itemstring = intent.getStringExtra("availableItems") ?: ""
        if (itemstring.isEmpty()) {
            // use 'result_first_user' as a dummy value for 'result_failed'
            // since the latter seems not to be present
            setResult(Activity.RESULT_FIRST_USER)
            closeActivity()
        } else { makeDialog(itemstring) }
        // (need to have else here since closeActivity seems to be asynchronous!)
    }
    
    private fun makeDialog( itemString: String ){
        val itemsList = itemString.split(";")
        availableItems = Array(itemsList.size){""}
        availableItemsToShow = Array(itemsList.size){""}
        for( i:Int in itemsList.indices ){
            val itemSplit = itemsList[i].split(":")
            val itemName = itemSplit[0]
            val itemPrice = itemSplit[1]
            availableItems!![i] = itemName
            availableItemsToShow!![i] = "$itemName ($itemPrice \u20AC)"
        }

        // create options dialog
        dialog = AlertDialog.Builder( this )
        formatDialog( availableItemsToShow!! )
        dialog!!.show()
    }

    // format the dialog window
    private fun formatDialog( options: Array<String> ){
        dialog!!.setTitle( R.string.chooseFromListInstruction )
        dialog!!.setSingleChoiceItems( options, -1) {
                _, which ->
            selectedIndex = which
        }
        dialog!!.setPositiveButton( R.string.enter ) {
                _, _ ->
            getItemFromDialog()
        }
        dialog!!.setNegativeButton( R.string.cancel ){
                _, _ ->
            cancelOrder()
        }
        dialog!!.create()
    }

    private fun getItemFromDialog(){
        if( selectedIndex < 0 || selectedIndex >= availableItems!!.size ){
            closeActivity() // return without controlled RESULT_CANCELED
        }
        setResult(Activity.RESULT_OK,resultIntent)
        resultIntent.putExtra("item", availableItems!![selectedIndex] )
        closeActivity()
    }

    private fun cancelOrder(){
        setResult(Activity.RESULT_CANCELED,resultIntent)
        closeActivity()
    }

    private fun closeActivity(){
        super.onPause()
        finish()
    }
}