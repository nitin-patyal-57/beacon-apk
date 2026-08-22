package com.walnut.beaconfinder.data.processing

class EmaRssiFilter(private val alpha: Double = 0.2) {

    private val filteredValues = mutableMapOf<String, Double>()

    fun filter(address: String, rawRssi: Int): Double {
        val previous = filteredValues[address]
        return if (previous == null) {
            rawRssi.toDouble().also { filteredValues[address] = it }
        } else {
            (alpha * rawRssi + (1.0 - alpha) * previous).also { filteredValues[address] = it }
        }
    }

    fun getFiltered(address: String): Double? = filteredValues[address]

    fun clear() = filteredValues.clear()

    fun remove(address: String) = filteredValues.remove(address)
}
