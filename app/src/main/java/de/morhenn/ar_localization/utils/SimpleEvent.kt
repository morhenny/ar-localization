package de.morhenn.ar_localization.utils

// Used as LiveData that represents an event without any data
open class SimpleEvent() {

    var hasBeenHandled = false
        private set // Allow external read but not write
        get() { //only return false once
            return if (!field) {
                field = true
                false
            } else {
                field
            }
        }
}