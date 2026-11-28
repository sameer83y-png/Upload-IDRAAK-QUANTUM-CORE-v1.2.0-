package com.idraak.quantum.core

/** Validates explicit learned aliases before they are persisted locally. */
object LearnedCommandPolicy {
    fun validate(phrase: String, target: String) {
        require(phrase.length in 2..100) { "Command phrase must contain 2 to 100 characters." }
        require(target.length in 2..120) { "Target command must contain 2 to 120 characters." }
        require(!LocalMemoryStore.containsCredentialMaterial(phrase)) {
            "Command phrase appears to contain credential material."
        }
        require(!LocalMemoryStore.containsCredentialMaterial(target)) {
            "Target command appears to contain credential material."
        }
    }
}
