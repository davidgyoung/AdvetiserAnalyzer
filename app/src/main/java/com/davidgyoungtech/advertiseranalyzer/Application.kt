package com.davidgyoungtech.advertiseranalyzer

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log


class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        this.startScanning()
    }

    fun startScanning() {
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val scanner = bluetoothAdapter.bluetoothLeScanner
        scanner.startScan(bleScannerCallback)
    }

    var lastChangeDetectionTime: Long = 0;
    var lastBinaryString: String = "";
    var servicesForBitPosition = HashMap<Integer, ArrayList<String>>()
    var presumedServiceUUidNumber = 0
    var dumped = false

    private val bleScannerCallback = object :ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result?.rssi?.let { it > -45 } == true) {
                result?.scanRecord?.getManufacturerSpecificData(0x004c)?.let {
                    val manData = it
                    if (manData.count() >= 17 && manData.get(0).toUByte() == 1.toUByte()) {
                        // We have found an apple background advertisement
                        var bytesAsBinary = "";
                        var bytesAsBinaryFormatted = "";
                        for (byteIndex in 1..16) {
                            var byteAsUnsignedInt = manData[byteIndex].toInt()
                            if (byteAsUnsignedInt < 0) {
                                byteAsUnsignedInt += 256
                            }
                            val binaryString = String.format("%8s", Integer.toBinaryString(byteAsUnsignedInt)).replace(" ", "0")
                            bytesAsBinary += binaryString
                            bytesAsBinaryFormatted += binaryString + " "
                        }
                        val firstBitSet = bytesAsBinary.indexOf("1")
                        val lastBitSet = bytesAsBinary.lastIndexOf("1")
                        if (lastBitSet != firstBitSet) {
                            Log.e(TAG, "Two bits set")
                        }
                        if (lastBinaryString != bytesAsBinary) {
                            if (lastBinaryString != "") {
                                if (System.currentTimeMillis() - lastChangeDetectionTime > 1700) {
                                    Log.e(TAG, "Failed to detect an advertisement change in "+(System.currentTimeMillis() - lastChangeDetectionTime)+" millis")
                                }
                                presumedServiceUUidNumber += 1
                            }
                            lastChangeDetectionTime = System.currentTimeMillis()
                            lastBinaryString = bytesAsBinary
                            val calculatedServiceUuid = formatServiceNumber(Integer(presumedServiceUUidNumber))
                            var services = servicesForBitPosition.get(Integer(firstBitSet))
                            if (services == null) {
                                services = ArrayList<String>()
                                servicesForBitPosition.put(Integer(firstBitSet), services)
                            }
                            else {
                                Log.d(TAG, "Collision detected for bit "+firstBitSet+".  Colliding service UUIDS:");
                                for (service in services) {
                                    Log.d(TAG, service)
                                }
                                Log.d(TAG, calculatedServiceUuid)
                            }
                            services.add(calculatedServiceUuid)
                            Log.d(TAG, "Apple bit for "+calculatedServiceUuid+" is "+String.format("%3d", firstBitSet)+": " +bytesAsBinaryFormatted)
                            Log.d(TAG, "Found "+servicesForBitPosition.count()+" of 128")
                            if (servicesForBitPosition.count() == 128) {
                                if (!dumped) {
                                    dumped = true
                                    dumpTable()
                                }
                            }
                        }

                    }
                }


            }
        }

        fun dumpTable() {
            Log.d(TAG, "// Table of known service UUIDs by position in Apple's proprietary background service advertisement")
            for (bitPosition in 0..127) {
                val uuid = servicesForBitPosition.get(Integer(bitPosition))
                val first = uuid?.get(0);
                Log.d(TAG, "\""+first+"\",")
            }

        }
        fun formatServiceNumber(number : Integer) : String {
            var serviceHex = String.format("%032X", number)
            val idBuff = StringBuffer(serviceHex)
            idBuff.insert(20, '-')
            idBuff.insert(16, '-')
            idBuff.insert(12, '-')
            idBuff.insert(8, '-')
            return idBuff.toString()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d(TAG,"onBatchScanResults:${results.toString()}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "onScanFailed: $errorCode")
        }
    }
    companion object {
        public const val TAG = "AdvertiserAnalyzerApp"

    }
}