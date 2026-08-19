package com.walnut.beaconfinder.data.model

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTING,
    FAILED
}
