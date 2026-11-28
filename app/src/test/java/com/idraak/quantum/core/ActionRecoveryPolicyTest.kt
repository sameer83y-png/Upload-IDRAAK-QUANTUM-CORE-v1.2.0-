package com.idraak.quantum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRecoveryPolicyTest {
    @Test
    fun disconnectedTargetAppIsNotReportedAsSuccessful() {
        assertTrue(ActionRecoveryPolicy.isExecutionFailure(
            "یہ ایپ ابھی Idraak Plugin Manager میں connected نہیں۔ پہلے اسے connect کریں۔"
        ))
    }

    @Test
    fun unsupportedTextSharingIsClassifiedAsFailure() {
        assertTrue(ActionRecoveryPolicy.isExecutionFailure(
            "یہ ایپ Android کے ذریعے text sharing سپورٹ نہیں کرتی۔"
        ))
    }

    @Test
    fun ambiguousContactNumberIsNotReportedAsSuccessful() {
        assertTrue(ActionRecoveryPolicy.isExecutionFailure(
            "اس رابطے کے ایک سے زیادہ نمبر ہیں۔ براہ کرم نمبر واضح کرکے دوبارہ کہیں۔"
        ))
    }

    @Test
    fun successfulShareHandoffIsNotClassifiedAsFailure() {
        assertFalse(ActionRecoveryPolicy.isExecutionFailure(
            "ایپ کا شیئر مینو کھل گیا ہے۔ پیغام چیک کرکے خود Send کریں۔"
        ))
    }

    @Test
    fun successfulDialerHandoffIsNotClassifiedAsFailure() {
        assertFalse(ActionRecoveryPolicy.isExecutionFailure(
            "فون ڈائلر کھول دیا گیا ہے۔ کال کرنے سے پہلے نمبر چیک کریں۔"
        ))
    }
}
