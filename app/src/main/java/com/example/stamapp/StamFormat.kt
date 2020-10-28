package com.example.stamapp.stamformat

import kotlin.math.roundToInt

// some helper classes and functions for sheet-to-program interface
// the input is typically a raw list of lists of strings read by the google sheets client

fun stringIsDouble( str: String ): Boolean{
    if ( str.isEmpty() ) { return false }
    try { str.toDouble() }
    catch ( nfe: NumberFormatException ) { return false }
    return true
}

fun stringIsInt( str: String ): Boolean{
    if ( str.isEmpty() ) { return false }
    try { str.toInt() }
    catch ( nfe: NumberFormatException ) { return false }
    return true
}

class SheetContentReader{
    // helper class doing the first parsing of a raw sheet content structure

    // variables for items
    private var item_row = -1 // row of the 'item' header
    private var item_column = -1 // column of the 'item' header
    private var code_row = -1 // row of 'code' header (column must be same as 'item')
    private var price_row = -1 // row of 'price' header (column must be same as 'item')
    private val items = mutableListOf<String>() // list of valid items
                                                // items are valid if they have a code and a price
    private val iteminfo = mutableListOf<Map<String,Any>>() // map of items to code and price and column in sheetcontent

    // variables for events
    private var event_column = -1 // column of the 'event' header (row must be same as 'item')
    private var last_event_column = -1 // column of last event in sheetcontent
    private val events = mutableListOf<String>() // list of events
    private val eventinfo = mutableListOf<Map<String,Any>>() // map of events to column in sheetcontent

    // variables for names
    private var name_row = -1 // row of the 'name' header
    private var last_name_row = -1 // row of last valid name (correctly handling intermediate empty rows)
    private var name_column = -1 // column of the 'name' header
    private val names = mutableListOf<String>() // list of all names
    private val nameinfo = mutableListOf<Map<String,Any>>() // map of names to row in sheetcontent
    private var total_column = -1 // column of the 'total' header (row must be same as 'name')

    // other variables
    private var content = arrayOf(arrayOf("")) // local reference to sheet content

    // empty constructor for initialization
    constructor(){
        // leave everything at default
    }

    // constructor using the content of a sheet
    constructor( sheetcontent: Array<Array<String>> ){
        content = sheetcontent
        // loop over rows and set position variables and names
        for( i in sheetcontent.indices) {
            if ("Item" in sheetcontent[i]) {
                item_row = i
                item_column = sheetcontent[i].indexOf("Item")
                // this row should also contain 'event' header
                // if not, event_column will remain at -1 and no events will be sought for or found
                if ("Evenement" in sheetcontent[i]){ event_column = sheetcontent[i].indexOf("Evenement") }
            }
            if ("Code" in sheetcontent[i]) { code_row = i }
            if ("Prijs" in sheetcontent[i]) { price_row = i }
            if ("Naam" in sheetcontent[i]) {
                name_row = i
                name_column = sheetcontent[i].indexOf("Naam")
                // this row should also contain 'total' header
                if ("Totaal" in sheetcontent[i]){ total_column = sheetcontent[i].indexOf("Totaal") }
                // catch exception of zero names
                if (i == sheetcontent.size - 1) {
                    last_name_row = i
                    break
                }
                // else loop over remaining rows and check if they contain valid names
                for (j in i+1 until sheetcontent.size) {
                    // allow empty rows in between valid rows
                    if (sheetcontent[j][name_column] == "") { continue }
                    // name is valid, fill info
                    names.add(sheetcontent[j][name_column])
                    nameinfo.add( mapOf( "name" to sheetcontent[j][name_column],
                                         "row" to j ))
                    last_name_row = j
                }
                break // assume name tag is below any other relevant tag
            }
        }
        // loop over columns and make items info
        for( j in item_column+1 until sheetcontent[item_row].size ){
            // stop when passing the 'event' header
            if( sheetcontent[item_row][j]=="Evenement"){ break }
            // allow empty columns between valid items
            if( sheetcontent[item_row][j].isEmpty() ){ continue }
            // found item candidate (name not empty), now check code and price
            if( sheetcontent[code_row][j].isEmpty() ) { continue }
            if( sheetcontent[price_row][j].isEmpty() ) { continue }
            val priceCandidate = sheetcontent[price_row][j].replace(",",".")
            // (allow both , and . as decimal characters)
            if( !stringIsDouble(priceCandidate)) { continue }
            // item is valid, fill info
            items.add(sheetcontent[item_row][j])
            iteminfo.add( mapOf( "item" to sheetcontent[item_row][j],
                                 "code" to sheetcontent[code_row][j],
                                 "price" to priceCandidate.toDouble(),
                                 "column" to j ))
        }
        // catch exception of no 'event' header
        if( event_column<0 ){ return }
        // catch exception of zero events
        if( event_column == sheetcontent[item_row].size - 1 ){ last_event_column = event_column; return }
        // else loop over columns and fill events info
        for( j in event_column+1 until sheetcontent[item_row].size ){
            // allow empty columns between valid items
            if( sheetcontent[item_row][j].isEmpty() ){ continue }
            // event is valid, fill info
            events.add(sheetcontent[item_row][j])
            eventinfo.add( mapOf(   "event" to sheetcontent[item_row][j],
                                    "column" to j ))
            last_event_column = j
        }
    }

    // getter methods for item variables
    fun getItemRow(): Int { return item_row }
    fun getItemColumn(): Int { return item_column }
    fun getCodeRow(): Int { return code_row }
    fun getPriceRow(): Int { return price_row }
    fun getItems(): List<String> { return ArrayList(items) }
    fun getItemPrice( item : String ): Double {
        for( i in 0 until iteminfo.size ){
            if( iteminfo[i]["item"]==item ){ return iteminfo[i]["price"].toString().toDouble() }
        }
        return -1.0
    }
    fun getItemCode( item : String ): String {
        for( i in 0 until iteminfo.size ){
            if( iteminfo[i]["item"]==item ){ return iteminfo[i]["code"].toString() }
        }
        return ""
    }
    fun getItemFromCode( code: String ): String {
        for( i in 0 until iteminfo.size ){
            if( iteminfo[i]["code"].toString()==code ){ return iteminfo[i]["item"].toString() }
        }
        return ""
    }
    fun getItemColumn( item : String ): Int{
        for( i in 0 until iteminfo.size ){
            if( iteminfo[i]["item"]==item ){ return iteminfo[i]["column"].toString().toInt() }
        }
        return -1
    }

    // getter methods for event variables
    fun getEventColumn(): Int { return event_column }
    fun getLastEventColumn(): Int { return last_event_column }
    fun getEvents(): List<String> { return ArrayList(events) }
    fun getEventColumn( event : String ): Int{
        for( i in 0 until eventinfo.size ){
            if( eventinfo[i]["event"]==event ){ return eventinfo[i]["column"].toString().toInt() }
        }
        return -1
    }

    // getter methods for name variables
    fun getNameRow(): Int { return name_row }
    fun getLastNameRow(): Int { return last_name_row }
    fun getNameColumn(): Int { return name_column }
    fun getTotalColumn(): Int { return total_column }
    fun getNames(): List<String> { return ArrayList(names) }
    fun getNameRow( name : String ): Int{
        for( i in 0 until nameinfo.size ){
            if( nameinfo[i]["name"]==name ){ return nameinfo[i]["row"].toString().toInt() }
        }
        return -1
    }

    // getter methods for other variables
    fun getContent(): Array<Array<String>> { return content }

    // special element getter and setter methods for content
    fun readContent( i:Int, j:Int): String{
        return content[i][j]
    }
    fun writeContent( i:Int, j:Int, value:String ){
        content[i][j] = value
    }

    // print information to console screen (mainly for debugging)
    fun print(){
        println("item_row: "+getItemRow().toString())
        println("item_column: "+getItemColumn().toString())
        println("code_row: "+getCodeRow().toString())
        println("price_row: "+getPriceRow().toString())
        println("name_row: "+getNameRow().toString())
        println("name_column: "+getNameColumn().toString())
    }
}

class StamClient{
    // a class representing the account of one person

    // name of this client
    private var name : String = ""
    // map matching items in price list to number of ordered items
    private val itemcounts = mutableMapOf< String, Int >()
    // map matching events to costs for this person
    private val eventcosts = mutableMapOf< String, Double >()
    // total debts
    private var totaldue = 0.0
    // reader object used to create this client
    private var reader : SheetContentReader

    // constructor using the content of a sheet and a given name
    constructor( sheetContentReader: SheetContentReader, requestname: String ){
        // set SheetContentReader instance
        reader = sheetContentReader
        // find row corresponding to requested name
        val thisnamerow = reader.getNameRow( requestname )
        // in case of invalid name, leave everything at default
        if( thisnamerow < 0){ return }
        // loop over items and fill item counts
        for ( item in reader.getItems() ) {
            val thisitemcolumn = reader.getItemColumn(item)
            if( stringIsInt(reader.readContent(thisnamerow,thisitemcolumn)) ){
                val thisitemamount = reader.readContent(thisnamerow,thisitemcolumn).toInt()
                itemcounts[item] = thisitemamount
            } else {
                itemcounts[item] = 0
            }
        }
        // loop over events and fill event costs
        for( event in reader.getEvents() ){
            val thiseventcolumn = reader.getEventColumn(event)
            if( stringIsDouble(reader.readContent(thisnamerow,thiseventcolumn)) ){
                val thiseventcost = reader.readContent(thisnamerow,thiseventcolumn).toDouble()
                eventcosts[event] = thiseventcost
            } else {
                eventcosts[event] = 0.0
            }
        }
        // set instance name attribute to requested name
        name = requestname
        // calculate total price due
        calctotaldue()
    }

    // getter methods
    fun name(): String { return name }
    fun itemcounts(): MutableMap<String,Int> { return HashMap(itemcounts) }
    fun eventcosts(): MutableMap<String,Double> { return HashMap(eventcosts)}
    fun totaldue(): Double { return totaldue }
    fun reader(): SheetContentReader { return reader }

    // function to return all necessary info in Array<String> format corresponding to sheet row
    fun makeRowInfo(): Array<String>{
        // copy row from sheet
        val row: Array<String> = reader.getContent()[reader.getNameRow(name)]
        // overwrite total debts
        row[reader.getTotalColumn()] = totaldue.toString()
        // overwrite item counts
        for( item: String in reader.getItems() ){
            row[reader.getItemColumn(item)] = itemcounts[item].toString()
        }
        // overwrite event costs
        for( event: String in reader.getEvents() ){
            row[reader.getEventColumn(event)] = eventcosts[event].toString()
        }
        return row
    }

    // calculate total debts
    private fun calctotaldue(): Double{
        var total = 0.0
        for( item: String in itemcounts.keys ){
            total += (itemcounts[item]?:0) * reader.getItemPrice(item)
        }
        for( event: String in eventcosts.keys ){
            total += eventcosts[event]?:0.0
        }
        totaldue = (total * 100).roundToInt()/100.0
        return totaldue
    }

    // methods for placing an order
    // return 0 if succeeded, -1 otherwise
    fun orderitem( item : String ): Int {
        if(item !in itemcounts.keys){ return -1 }
        val orig = itemcounts[item]
        itemcounts[item] = orig!! + 1
        // recalculate total debts
        calctotaldue()
        return 0
    }

    // methods for placing an event cost
    fun addeventcost( event: String, cost: Double ): Int{
        if( event !in eventcosts.keys ){ return -1 }
        val orig = eventcosts[event]
        eventcosts[event] = orig!! + cost
        calctotaldue()
        return 0
    }
}

class A1Parser{
    // a class to parse between strings in A1 notation and row/column indices

    private var a1 = ""
    private var sheetname = ""
    private var firstrow = -1
    private var nrows = 0
    private var firstcolumn = -1
    private var ncolumns = 0

    constructor( a1notation: String ){
        a1 = a1notation
        // find sheet name
        var temp = a1notation.split("!").toTypedArray()
        sheetname = temp[0]
        // find upper left and lower right cell ID
        temp = temp[1].split(":").toTypedArray()
        val upleft = temp[0]
        val downright = temp[1]
        // convert to indices
        val upleftarray = cellIDToIndices( upleft )
        val downrightarray = cellIDToIndices( downright )
        firstrow = upleftarray[0]
        firstcolumn = upleftarray[1]
        nrows = downrightarray[0]-firstrow+1
        ncolumns = downrightarray[0]-firstcolumn+1
    }

    constructor( sheet:String, firstRow:Int, nRows:Int, firstColumn:Int, nColumns:Int ){
        sheetname = sheet
        firstrow = firstRow
        nrows = nRows
        firstcolumn = firstColumn
        ncolumns = nColumns
        var a1notation = "$sheet!"
        a1notation += indicesToCellID(firstRow,firstColumn)+":"
        a1notation += indicesToCellID(firstRow+nRows-1, firstColumn+nColumns-1)
        a1 = a1notation
    }

    // getters
    fun a1(): String { return a1 }
    fun sheetname(): String { return sheetname }
    fun firstrow(): Int { return firstrow }
    fun firstcolumn(): Int { return firstcolumn }
    fun nrows(): Int { return nrows }
    fun ncolumns(): Int { return ncolumns }

    companion object{
        fun cellIDToIndices( cellid: String ): Array<Int> {
            // convert a string in the form of A12 or ZZ8 to corresponding row and column number
            // step 1: split cell ID into row and column part
            var splitindex = -1
            for( i in cellid.indices){
                if(stringIsInt(cellid[i].toString())){ splitindex=i; break }
            }
            val colstr = cellid.substring(0, splitindex)
            val row = cellid.substring(splitindex).toInt()-1
            // step 2: convert row notation to row number
            // note: ASCII value of 'A' is 65
            val column = 26*(colstr.length-1) + (colstr[colstr.length-1].toInt() - 65)
            return arrayOf(row,column)
        }
        fun indicesToCellID( row:Int, column:Int): String {
            // inverse function of cellIDToIndices
            return (column + 65).toChar()+(row+1).toString()
        }
    }
}