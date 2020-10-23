package com.example.stamapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class ManualOrderActivity : AppCompatActivity() {

    private var availableItems: Array<String>? = null
    private var selectedIndex: Int = -1
    private var dialog: AlertDialog.Builder? = null
    private val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_order)

        // get array of available items from intent
        val itemslist = intent.getStringExtra("availableItems")?.split(";")
        if( itemslist==null ){ closeActivity() } // return without controlled RESULT_CANCELED
        if( itemslist!!.isEmpty() ){ closeActivity() } // return without controlled RESULT_CANCELED
        availableItems = Array(itemslist.size){""}
        for( i:Int in itemslist.indices ){ availableItems!![i] = itemslist[i] }

        // create options dialog
        dialog = AlertDialog.Builder( this )
        formatDialog( availableItems!! )
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