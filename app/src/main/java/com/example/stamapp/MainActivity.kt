package com.example.stamapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkInfo
// (deprecation warning probably harmless as explicit version check is implemented)
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.*
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stamapp.stamformat.A1Parser
import com.example.stamapp.stamformat.SheetContentReader
import com.example.stamapp.stamformat.StamClient
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
// (deprecated and replaced by GoogleCredentials and HttpCredentialsAdapter, but keep for reference)
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.security.GeneralSecurityException
import java.util.*
import kotlin.system.exitProcess

// TODO: testing, testing, testing, ....
// TODO: test with real bar codes
// TODO: start documenting...

class MainActivity : AppCompatActivity() {

    ////////////////////////////
    /// values and variables ///
    ////////////////////////////

    /// values and variables related to internet connection ///
    private val requestConnectionCallID: Int = 0 // call identifier to request connection activity
    private var internetInitiallyOff = false // keep track if internet connection was off on startup
    private val turnOffConnectionCallID: Int = 6 // call identifier to turn off internet upon closing
    /// values and variables related to user authentication ///
    private var userName: String = "" // username
    private val loginActivityCallID: Int = 1 // call identifier to login activity
    private var pref: SharedPreferences? = null // object to store username and other persistent settings in
    /// values and variables related to sheet interface ///
    private var sheetService: Sheets? = null // store sheet service globally for faster reading and writing
    private var sheetContentReader: SheetContentReader = SheetContentReader() // object encoding sheet info
    private var thisClient: StamClient? = null // object representing this client
    /// values and variables related to barcode scanning ///
    private val scanCodeActivityCallID: Int = 2 // call identifier to scan code activity
    /// values and variables related to manual ordering ///
    private val manualOrderActivityCallID: Int = 3 // call identifier to place manual order
    /// values and variables related to shared cost ///
    private val sharedCostActivityCallID: Int = 4 // call identifier to enter shared cost
    /// values and variables related to individual cost ///
    private val individualCostActivityCallID: Int = 5 // call identifier to enter individual cost
    /// widgets modified by MainActivity ///
    private var userNameTextView: TextView? = null
    private var processScreenDialog: AlertDialog? = null
    private var boundaryList: List<View>? = null

    ///////////////////////////////////
    /// main process flow functions ///
    ///////////////////////////////////

    // function automatically called upon opening the app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // make processing screen dialog
        val psBuilder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        // short version (only in API >= 21)
        //psBuilder.setView(R.layout.activity_processing_screen)
        // long version ( all APIs )
        val inflater: LayoutInflater = applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.activity_processing_screen,null)
        psBuilder.setView( view )
        processScreenDialog = psBuilder.create()
        processScreenDialog?.setCanceledOnTouchOutside(false)

        // get boundary widgets
        boundaryList = listOf(  findViewById(R.id.boundaryTop),
                                findViewById(R.id.boundaryBottom),
                                findViewById(R.id.boundaryLeft),
                                findViewById(R.id.boundaryRight) )

        // continue workflow
        onCreated()
    }

    // continue workflow after creation of MainActivity
    private fun onCreated(){

        // check internet connection
        val isConnected: Boolean = isOnline()
        if( isConnected ){ onInternetConnected() }
        else{
            internetInitiallyOff = true
            requestConnection()
        }
    }

    // continue workflow after internet connection is established
    private fun onInternetConnected(){

        // establish connection to sheet
        sheetService = createSheetsService()

        // initialise sheet content object (first needed for login)
        sheetContentReader = readSheet()

        // check if already logged in and if not, set user name
        pref = getSharedPreferences("com.example.stamapp.preferences", Context.MODE_PRIVATE)
        val testUserName = pref?.getString("USERNAME","")?:""
        if( testUserName.isEmpty() ){ setUserName() }
        else{
            userName = testUserName
            // check if stored username is actually in sheet
            if( userName !in sheetContentReader.getNames() ){
                Toast.makeText( applicationContext,
                    R.string.loginNameNotFound,
                    Toast.LENGTH_LONG ).show()
                    setUserName()
            }
            else { onLogin() }
        }
    }

    // continue workflow after login
    private fun onLogin(){

        // set text for user name text view
        userNameTextView = findViewById(R.id.userNameTextView)
        userNameTextView?.setTextColor(Color.BLUE)
        userNameTextView?.text = getString(R.string.userNameTextView_text,userName)

        // initialize client
        // error handling: in principle not necessary since it was already checked
        // that userName corresponds to a valid row in sheetContentReader
        thisClient = StamClient( sheetContentReader, userName )

        // open camera if set to open by default
        val openCamera = pref?.getBoolean("DEFAULT_CAMERA_ON_STARTUP",false)?:false
        if( openCamera ){ scanCode() }
    }

    //////////////////////////////////////////////////////
    /// helper functions related to widgets and layout ///
    //////////////////////////////////////////////////////

    private fun showColouredBoundary( millis: Long, color: String ){
        for( view: View in boundaryList!! ){
            view.setBackgroundColor(Color.parseColor(color))
            view.visibility = View.VISIBLE
        }
        Handler().postDelayed( Runnable {
            for( view: View in boundaryList!!){ view.visibility = View.INVISIBLE }
        }, millis)
    }

    private fun showGreenBoundary( millis: Long = 2000 ){
        showColouredBoundary( millis, "#0ACF1f" )
    }

    private fun showRedBoundary( millis: Long = 2000 ){
        showColouredBoundary( millis, "#B00020" )
    }

    //////////////////////////////////////////////////////
    /// helper functions related to network connection ///
    //////////////////////////////////////////////////////

    // checking internet connection
    private fun isOnline(): Boolean {
        val isOnline: Boolean
        val conMan = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ){
            val networkCaps = conMan.getNetworkCapabilities(conMan.activeNetwork)
            isOnline = networkCaps?.hasCapability(NET_CAPABILITY_INTERNET)?:false
        } else {

            val networkInfo: NetworkInfo? = conMan.activeNetworkInfo
            isOnline = networkInfo?.isConnected==true
        }
        return isOnline
    }

    // request the user to turn on internet
    // note: connection checking is not included, do this before calling this function!
    private fun requestConnection(){
        val dialog = AlertDialog.Builder( this@MainActivity )
        dialog.setTitle(R.string.connectionDialogTitle)
        dialog.setMessage(R.string.connectionDialogMessage)
        dialog.setPositiveButton( R.string.connectionDialogPositive ) {
                _, _ ->
            // start settings activity
            val internetIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            startActivityForResult( internetIntent, requestConnectionCallID )
        }
        dialog.setNegativeButton( R.string.connectionDialogNegative ){
                _, _ ->
            // close app
            exitApp()
        }
        dialog.create()
        dialog.show()
    }

    /////////////////////////////////////////
    /// helper functions related to login ///
    /////////////////////////////////////////

    // call the login activity to set the (persistent) user name for this device
    private fun setUserName( ){
        // read sheet content and format list of existing names
        var existingNames = ""
        for( name: String in sheetContentReader.getNames() ){
            existingNames += "$name;"
        }
        existingNames = existingNames.dropLast(1) // remove final ; character
        val intent = Intent(this,LoginActivity::class.java)
        intent.putExtra("existingNames", existingNames)
        startActivityForResult(intent,loginActivityCallID)
    }

    ////////////////////////////////////////////////////
    /// helper functions related to barcode scanning ///
    ////////////////////////////////////////////////////

    // start ScanCodeActivity
    // (linked to "scan" button)
    fun scanCode(@Suppress("UNUSED_PARAMETER")view: View){
        sheetContentReader = readSheet()
        val intent = Intent(this,ScanCodeActivity::class.java)
        startActivityForResult(intent,scanCodeActivityCallID)
    }


    // copy of above but not linked to a view (for calling without button press)
    private fun scanCode(){
        val intent = Intent(this,ScanCodeActivity::class.java)
        startActivityForResult(intent,scanCodeActivityCallID)
    }

    // linking a code to an item name and calling the corresponding order function
    private fun placeOrderFromCode( client: StamClient, code: String ){
        val item : String = sheetContentReader.getItemFromCode( code )
        if( item=="" ){
            showRedBoundary()
            Toast.makeText(
                applicationContext,
                R.string.unrecognizedCode,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        placeOrderFromItemName( client, item )
    }

    ///////////////////////////////////////////////////////
    /// helper function related to manual order placing ///
    ///////////////////////////////////////////////////////

    // start ManualOrderActivity
    // (linked to "order" button)
    fun manualOrder(@Suppress("UNUSED_PARAMETER")view: View){
        sheetContentReader = readSheet()
        var availableItems = ""
        for( item:String in sheetContentReader.getItems() ){
            val itemPrice = sheetContentReader.getItemPrice(item).toString()
            availableItems += "$item:$itemPrice;"
        }
        availableItems = availableItems.dropLast(1) // remove final ; character
        val intent = Intent(this,ManualOrderActivity::class.java)
        intent.putExtra("availableItems", availableItems)
        startActivityForResult(intent,manualOrderActivityCallID)
    }

    ////////////////////////////////////////////////
    /// helper functions related to shared costs ///
    ////////////////////////////////////////////////

    // start SharedCostActivity
    // (linked to "shared cost" button)
    fun sharedCost(@Suppress("UNUSED_PARAMETER")view: View){
        sheetContentReader = readSheet()
        // format list of existing names
        var existingNames = ""
        for( name:String in sheetContentReader.getNames() ){
            existingNames += "$name;"
        }
        existingNames = existingNames.dropLast(1) // remove final ; character
        // format list of existing events
        var existingEvents = ""
        for( event:String in sheetContentReader.getEvents() ){
            existingEvents += "$event;"
        }
        existingEvents = existingEvents.dropLast(1) // remove final ; character
        val intent = Intent(this,SharedCostActivity::class.java)
        intent.putExtra("existingNames", existingNames)
        intent.putExtra("existingEvents", existingEvents)
        startActivityForResult(intent,sharedCostActivityCallID)
    }

    ///////////////////////////////////////////////////
    /// helper functions related to individual cost ///
    ///////////////////////////////////////////////////

    // start IndividualCostActivity
    // (linked to "individual cost" button)
    fun individualCost(@Suppress("UNUSED_PARAMETER")view: View){
        sheetContentReader = readSheet()
        // read sheet content and format list of existing events
        var existingEvents = ""
        for( event:String in sheetContentReader.getEvents() ){
            existingEvents += "$event;"
        }
        existingEvents = existingEvents.dropLast(1) // remove final ; character
        val intent = Intent(this,IndividualCostActivity::class.java)
        intent.putExtra("existingEvents", existingEvents)
        startActivityForResult(intent,individualCostActivityCallID)
    }

    //////////////////////////////////////////////////////////////
    /// low-level functions related to google sheets interface ///
    //////////////////////////////////////////////////////////////

    // internal helper function for accessing google sheet services
    // uses OAuth 2.0 ID, requiring client-side authentication
    // DEPRECATED, replaced by credentials from service account!
    @Throws(IOException::class)
    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential? {
        val JSON_FACTORY = JacksonFactory.getDefaultInstance()
        val TOKENS_DIRECTORY_PATH = "tokens"
        val SCOPES = getString(R.string.sheet_scopes)
        val inStream: InputStream = resources.openRawResource(R.raw.credentials)
        val clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inStream))

        // Build flow and trigger user authorization request.
        val tokenFolder =
            File(applicationContext.getExternalFilesDir(""),TOKENS_DIRECTORY_PATH)
        if (!tokenFolder.exists()) {
            tokenFolder.mkdirs()
        }
        // ERROR using FileDataStoreFactory,
        // seems to be a known issue https://github.com/googleapis/google-api-java-client/issues/1382
        // and here https://github.com/googleapis/google-http-java-client/issues/906
        // but not sure how to solve... adding newest http client in gradle does not seem to work...
        // switching to API 26 instead of 24
        val flow = GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Collections.singletonList(SCOPES))
                    .setDataStoreFactory(FileDataStoreFactory(tokenFolder)).setAccessType("offline").build()

        // ERROR using AuthorizationCodeInstalledApp,
        // seems to be a known issue https://stackoverflow.com/questions/60345664/authorizationcodeinstalledapp-android-alternative
        // but error message is different and solution does not seem to work.
        // Seems to be fixed by downgrading google-auth-client-jetty in the gradle file
        val authorizationCode : AuthorizationCodeInstalledApp =
            object : AuthorizationCodeInstalledApp(flow, LocalServerReceiver()){
                @Throws(IOException::class)
                override fun onAuthorization(authorizationUrl: AuthorizationCodeRequestUrl) {
                    val url = authorizationUrl.build()
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    applicationContext.startActivity(browserIntent)
                }
            }
        return authorizationCode.authorize("user")
    }

    // internal helper function for accessing google sheet services
    // using a server account, not requiring client-side authentication
    @Throws(IOException::class)
    private fun getCredentialsFromServiceAccount(HTTP_TRANSPORT: NetHttpTransport): HttpRequestInitializer {
        val scopes = getString(R.string.sheet_scopes)
        val inStream: InputStream = resources.openRawResource(R.raw.servicecredentials)
        // following works but seems to be deprecated...
        // note that the 'credential' variable should be returned which is of type Credential?
        //val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        //val credential = GoogleCredential.fromStream(inStream,HTTP_TRANSPORT,jsonFactory)
        //    .createScoped(Collections.singletonList(scopes))
        //return credential
        // new version using GoogleCredentials instead of GoogleCredential...
        // note that the return type should be of type HttpRequestInitializer
        val credential = GoogleCredentials.fromStream(inStream)
            .createScoped(Collections.singletonList(scopes))
        return HttpCredentialsAdapter(credential)
    }

    // internal helper function to create a connection service to a google sheet
    @Throws(IOException::class, GeneralSecurityException::class)
    private fun createSheetsService(): Sheets? {
        val httpTransport = NetHttpTransport()
        val jsonFactory: JsonFactory =
            JacksonFactory.getDefaultInstance()
        // option 1: requires client-side google account login using OAuth ID
        //val credential: Credential? = getCredentials(httpTransport)
        //return com.google.api.services.sheets.v4.Sheets.Builder(httpTransport, jsonFactory, credential)
        //    .setApplicationName("StamApp")
        //    .build()
        // option 2: requires no google login but does not work for writing
        //return com.google.api.services.sheets.v4.Sheets.Builder(httpTransport, jsonFactory, null)
        //    .setApplicationName("StamApp")
        //    .build()
        // option 3: requires server-side authentication using service account
        val credential: HttpRequestInitializer = getCredentialsFromServiceAccount(httpTransport)
        return Sheets.Builder(httpTransport,jsonFactory,credential)
            .setApplicationName("StamApp")
            .build()
    }

    // utility function to read the values from a given sheet and range
    // input args:
    // - a Sheets? object, typically created using createSheetsService()
    // - a String object, encoding the range to read in A1 notation
    // output: a list of lists of strings representing the contents of the given range
    @Throws(IOException::class, GeneralSecurityException::class)
    @Suppress("UNCHECKED_CAST") // cast response["values"] to ArrayList<ArrayList<String>>
    private fun readSheetRange(sheetsService: Sheets?, range: String): Array<Array<String>>{
        val spreadsheetID= getString(R.string.sheet_id)
        val apiKey = getString(R.string.sheet_api_key)
        val request =
            sheetsService!!.spreadsheets().values()[spreadsheetID, range]
        request.key = apiKey
        val response = request.execute()
        val sheetValues = response["values"] as ArrayList<ArrayList<String>>
        // conversion to fixed-size array
        val nrows = sheetValues.size
        var ncolumns = 0
        for( i in 0 until nrows ){ if(sheetValues[i].size > ncolumns){ ncolumns = sheetValues[i].size} }
        val sheetValuesArray = Array(nrows){Array(ncolumns){""}}
        for( i in 0 until nrows){ for( j in 0 until sheetValues[i].size){ sheetValuesArray[i][j]=sheetValues[i][j] } }
        return sheetValuesArray
    }

    // utility function to write the values to a given sheet and range
    // input args:
    // - a Sheets? object, typically created using createSheetsService()
    // - a String object, encoding the range to write to in A1 notation
    // - an array of arrays of strings representing the contents to write
    @Throws(IOException::class, GeneralSecurityException::class)
    private fun writeSheetRange(sheetsService: Sheets?,
                                range: String,
                                values: Array<Array<String>>){
        val spreadsheetID = getString(R.string.sheet_id)
        val apiKey = getString(R.string.sheet_api_key)
        val body = com.google.api.services.sheets.v4.model.ValueRange()
        // type conversion of values, maybe change type later
        val valuesList = mutableListOf<MutableList<Any>>()
        for( value: Array<String> in values ){ valuesList.add( value.toMutableList()) }
        body.setValues(valuesList as List<MutableList<Any>>?)
        val request =
            sheetsService!!.spreadsheets().values()
                .update(spreadsheetID, range, body)
                .setValueInputOption("RAW")
        request.key = apiKey
        request.execute()
    }

    // internal debugging function to print out array of array of strings
    /*private fun printGrid( struct: Array<Array<String>>? ){
        if( struct==null ){ println("null"); return}
        var res = "["
        for( i in struct.indices){
            res += " ["
            for( j in 0 until struct[i].size-1 ){
                res += struct[i][j]+", "
            }
            if( i==struct.size-1) {res += struct[i][struct[i].size-1]+"] ]"}
            else {res += struct[i][struct[i].size-1]+"]\n"}
        }
        println(res)
    }*/

    ////////////////////////////////////////////////////////////////
    //// high-level functions related to google sheets interface ///
    ////////////////////////////////////////////////////////////////

    // read entire sheet
    private fun readSheet(): SheetContentReader {
        val range = getString(R.string.sheet_range)
        var sheetContentReader = SheetContentReader()
        // make coroutine to avoid NetworkOnMainThreadException
        val scope = CoroutineScope(Dispatchers.IO)
        val scopeState = scope.async(Dispatchers.IO){
            val sheetContent: Array<Array<String>> = readSheetRange(sheetService, range)
            sheetContentReader = SheetContentReader(sheetContent)
        }
        runBlocking { scopeState.await() }
        return sheetContentReader
    }

    // (over-)write limited range in sheet
    // arguments:
    // - text = Array<Array<String>> containing cell values
    // - row, column = upper left corner where to start writing
    private fun writeToSheet( text: Array<Array<String>>, row: Int, column: Int){
        val a1parser = A1Parser("Sheet1",row, text.size,
            column, text[0].size)
        // make coroutine to avoid NetworkOnMainThreadException
        val scope = CoroutineScope(Dispatchers.IO)
        val scopeState = scope.async(Dispatchers.IO){
            writeSheetRange(sheetService,a1parser.a1(),text)
        }
        runBlocking { scopeState.await() }
    }

    // write the data of a single user to the sheet and re-read the sheet
    private fun writeClientInfoToSheet( client: StamClient ){
        val row: Int = sheetContentReader.getNameRow(client.name())
        val info: Array<String> = client.makeRowInfo()
        writeToSheet( arrayOf(info), row,0)
        sheetContentReader = readSheet()
    }

    // add a new username to the sheet and re-read the sheet
    private fun addNewUserToSheet( userName: String ){
        val row: Int = sheetContentReader.getLastNameRow()+1
        val column: Int = sheetContentReader.getNameColumn()
        writeToSheet( arrayOf(arrayOf(userName)), row, column )
        showGreenBoundary()
        sheetContentReader = readSheet()
    }

    // add a new event to sheet and re-read sheet
    private fun addNewEventToSheet( description: String ){
        val row: Int = sheetContentReader.getItemRow()
        val column: Int = sheetContentReader.getLastEventColumn()+1
        writeToSheet( arrayOf(arrayOf(description)), row, column )
        sheetContentReader = readSheet()
    }

    // place an order for given client and item name
    private fun placeOrderFromItemName( client: StamClient, item: String ){

        // check if item is in list of valid items (in sheet reader)
        // (in normal usage this check is superfluous as the list of items is generated from the sheet reader but keep for extra safety
        if (item !in client.reader().getItems()) {
            Toast.makeText(
                applicationContext,
                R.string.unrecognizedItem,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            showDialog()
            client.orderitem(item)
            withContext(Dispatchers.IO){ writeClientInfoToSheet(client) }
            dismissDialog()
            showGreenBoundary()
            Toast.makeText(
                applicationContext,
                getString(R.string.orderSuccess,item),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // put costs for given users for a given event
    private fun addCostsForEvent( eventName: String, userNames: Array<String>, costPerPerson: Double ){
        CoroutineScope(Dispatchers.Main).launch {
            showDialog()
            // re-read sheet for safety
            sheetContentReader = readSheet()
            // check if event is in sheet content
            if (eventName !in sheetContentReader.getEvents()) {
                withContext(Dispatchers.IO){ addNewEventToSheet(eventName) }
            }
            for (userName in userNames) {
                // check if user name is in sheet content
                // (in normal usage this check is superfluous as the list of users is generated from the sheet reader but keep for extra safety)
                if (userName !in sheetContentReader.getNames()) {
                    withContext(Dispatchers.IO){ addNewUserToSheet(userName) }
                }
                val client = StamClient(sheetContentReader, userName)
                client.addeventcost(eventName, costPerPerson)
                withContext(Dispatchers.IO){writeClientInfoToSheet(client)}
            }
            dismissDialog()
            showGreenBoundary()
            Toast.makeText(
                applicationContext,
                R.string.costSuccess,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /////////////////////////////
    /// close the application ///
    /////////////////////////////

    fun exitApp(@Suppress("UNUSED_PARAMETER")view: View){
        if( internetInitiallyOff ){
            val dialog = AlertDialog.Builder( this@MainActivity )
            dialog.setTitle(R.string.closeConnectionDialogTitle)
            dialog.setMessage(R.string.closeConnectionDialogMessage)
            dialog.setPositiveButton( R.string.closeConnectionDialogPositive ) {
                    _, _ ->
                // start settings activity
                val internetIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                startActivityForResult( internetIntent, turnOffConnectionCallID )
            }
            dialog.setNegativeButton( R.string.no ){
                    _, _ ->
                // close app
                exitApp()
            }
            dialog.create()
            dialog.show()
        }
        else {
            finish()
            exitProcess(0)
        }
    }

    private fun exitApp(){
        // copy of above but without view to call programmatically
        finish()
        exitProcess(0)
    }

    private fun showDialog(): Int{
        processScreenDialog?.show()
        return 1
    }
    private fun dismissDialog(): Int{
        processScreenDialog?.dismiss()
        return 1
    }

    ////////////////////////
    /// onActivityResult ///
    ////////////////////////
    // (automatically called after termination of any secondary activity)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when( requestCode ){
            turnOffConnectionCallID -> {
                // close the app (after internet connection has been closed again by the user)
                exitApp()
            }
            requestConnectionCallID -> {
                // return to beginning of app workflow (re-check for connection)
                // but first allow some time to connect
                CoroutineScope(Dispatchers.Main).launch {
                    showDialog()
                    withContext(Dispatchers.IO){
                        val scope = CoroutineScope(Dispatchers.IO)
                        val scopeState = scope.async(Dispatchers.IO){
                            Thread.sleep(3000)
                        }
                        runBlocking { scopeState.await() }
                    }
                    dismissDialog()
                    onCreated()
                }
            }
            loginActivityCallID -> {
                if( resultCode==Activity.RESULT_OK){
                    showGreenBoundary()
                    Toast.makeText(
                        applicationContext,
                        R.string.loginSuccessful,
                        Toast.LENGTH_LONG
                    ).show()
                    userName = data!!.getStringExtra("userName")?:""
                    val prefEditor = pref?.edit()
                    prefEditor?.putString("USERNAME", userName)
                    prefEditor?.apply()
                    addNewUserToSheet(userName)
                    // continue process flow
                    onLogin( )
                }
                else{
                    // should not normally happen...
                    exitApp()
                }
            }
            scanCodeActivityCallID -> {
                when( resultCode ){
                    Activity.RESULT_CANCELED -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.cameraCanceled,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Activity.RESULT_OK -> {
                        var scannedCode: String = data!!.getStringExtra("code")?:""
                        scannedCode = 2.toString() // for testing!!!
                        placeOrderFromCode( thisClient!!, scannedCode )
                    }
                    else -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.cameraClosedUnexpectedly,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            manualOrderActivityCallID -> {
                when( resultCode ){
                    Activity.RESULT_CANCELED -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.orderCanceled,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Activity.RESULT_OK -> {
                        val orderedItem: String = data!!.getStringExtra("item")?:""
                        placeOrderFromItemName( thisClient!!, orderedItem )
                    }
                    Activity.RESULT_FIRST_USER -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.noItemsInSheet,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.orderError,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            sharedCostActivityCallID -> {
                when( resultCode ){
                    Activity.RESULT_CANCELED -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.costCanceled,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Activity.RESULT_OK -> {
                        val costPerPerson: Double = data!!.getDoubleExtra("costPerPerson",0.0 )
                        val eventName: String = data.getStringExtra("eventName")!!
                        val isNewEvent: Boolean = data.getBooleanExtra("isNewEvent", true)
                        val sharingUsers: ArrayList<String> = data.getStringArrayListExtra("sharingUsers")!!
                        if( isNewEvent ){ addNewEventToSheet( eventName ) }
                        addCostsForEvent( eventName, sharingUsers.toTypedArray(), costPerPerson )
                    }
                    else -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.costError,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            individualCostActivityCallID -> {
                when( resultCode ){
                    Activity.RESULT_CANCELED -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.costCanceled,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Activity.RESULT_OK -> {
                        val cost: Double = data!!.getDoubleExtra("cost",0.0)
                        val eventName: String = data.getStringExtra("eventName")!!
                        val isNewEvent: Boolean = data.getBooleanExtra("isNewEvent",true)
                        if( isNewEvent ){ addNewEventToSheet(eventName) }
                        addCostsForEvent( eventName, arrayOf(userName), cost )
                    }
                    else -> {
                        showRedBoundary()
                        Toast.makeText(
                            applicationContext,
                            R.string.costError,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            else -> {
                // no other activity ID's are expected
            }
        }
    }

    /////////////////////////////
    /// menu options handling ///
    /////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu)

        // set default behaviour for menu item 'default camera on startup'
        val isInitiallyChecked = pref?.getBoolean("DEFAULT_CAMERA_ON_STARTUP",false)?:false
        menu?.findItem(R.id.default_camera_on_startup)?.isChecked = isInitiallyChecked
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.default_camera_on_startup -> {
                // edit the checked state based on click event
                val prefEditor = pref?.edit()
                if( item.isChecked ) {
                    item.isChecked = false
                    prefEditor?.putBoolean("DEFAULT_CAMERA_ON_STARTUP",false)
                }
                else {
                    item.isChecked = true
                    prefEditor?.putBoolean("DEFAULT_CAMERA_ON_STARTUP", true)
                }
                prefEditor?.apply()
                true
            }
            else -> {
                // no other options implemented for now
                false
            }
        }
    }
}