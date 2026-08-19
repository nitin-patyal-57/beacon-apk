package com.walnut.beaconfinder.data.model

data class GattServiceInfo(
    val uuid: android.os.ParcelUuid,
    val characteristics: List<GattCharacteristicInfo> = emptyList()
)

data class GattCharacteristicInfo(
    val uuid: android.os.ParcelUuid,
    val properties: Int,
    val descriptors: List<android.os.ParcelUuid> = emptyList(),
    val value: ByteArray? = null
) {
    val canRead: Boolean get() = (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ) != 0
    val canWrite: Boolean get() = (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
    val canWriteNoResponse: Boolean get() = (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    val canNotify: Boolean get() = (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
    val canIndicate: Boolean get() = (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattCharacteristicInfo) return false
        return uuid == other.uuid
    }

    override fun hashCode(): Int = uuid.hashCode()
}
