package com.example.stamapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Detector.Detections
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import java.io.IOException


class ScanCodeActivity : AppCompatActivity() {

    var cameraSurfaceView: SurfaceView? = null
    var barcodeTextView: TextView? = null
    private var barcodeDetector: BarcodeDetector? = null
    private var cameraSource: CameraSource? = null
    private val REQUEST_CAMERA_PERMISSION = 201
    private var cancelScanButton: Button? = null
    var scannedCode = ""
    val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_code)

        initViews()
        initialiseScanner()
    }

    // maybe remove this function later? seems to be superfluous
    private fun initViews(){
        barcodeTextView = findViewById(R.id.barcodeTextView)
        cameraSurfaceView = findViewById(R.id.cameraSurfaceView)
        cancelScanButton = findViewById(R.id.cancelScanButton)
    }

    // initialise the scanner object
    private fun initialiseScanner(){
        // build barcode detector object
        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()

        // assign a processor to the barcode detector that scans for valid codes
        barcodeDetector!!.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
            }

            override fun receiveDetections(detections: Detections<Barcode>) {
                // how to handle valid detection
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0) {
                    barcodeTextView?.post { // TODO: what does this mean?
                        scannedCode = barcodes.valueAt(0).displayValue.toString()
                        resultIntent.putExtra("code", scannedCode)
                        setResult(Activity.RESULT_OK, resultIntent)
                        cameraSource?.stop()
                        closeActivity()
                    }
                }
            }
        })

        // build linker object between detector and camera
        cameraSource = CameraSource.Builder(this, barcodeDetector)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true)
            .build()

        // build camera preview handler
        cameraSurfaceView!!.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    // if correct permissions are granted, start the camera source
                    if (ActivityCompat.checkSelfPermission(
                            this@ScanCodeActivity,
                            Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                        cameraSource?.start(cameraSurfaceView!!.holder)
                    }
                    // if not, dynamically request permission
                    else {
                        ActivityCompat.requestPermissions(
                            this@ScanCodeActivity,
                            arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION)
                        return // onRequestPermissionsResult is called automatically
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                // no special behaviour needed in this case
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // stop the camera source upon closing the camera preview
                cameraSource?.stop()
            }
        })
    }

    fun closeActivity(){
        super.onPause()
        barcodeDetector?.release()
        finish()
    }

    fun onCancel(@Suppress("UNUSED_PARAMETER")view : View) {
        setResult(Activity.RESULT_CANCELED,resultIntent)
        closeActivity()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray){
        when( requestCode ){
            REQUEST_CAMERA_PERMISSION -> {
                if( grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED ){
                    Toast.makeText( applicationContext,
                                    getString(R.string.camera_granted),
                                    Toast.LENGTH_LONG).show()
                }
                else{
                    Toast.makeText( applicationContext,
                        getString(R.string.camera_notgranted),
                        Toast.LENGTH_LONG).show()
                }
                this.onCancel( View(applicationContext) )
            }
            else -> {
                // no other requests expected
            }
        }
    }
}