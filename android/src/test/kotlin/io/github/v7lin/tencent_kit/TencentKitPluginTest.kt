package io.github.v7lin.tencent_kit

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * This demonstrates a simple unit test of the Kotlin portion of this plugin's implementation.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */
class TencentKitPluginTest {
    @Test
    fun onMethodCall_isQQInstalled_returnsExpectedValue() {
        val plugin = TencentKitPlugin()

        val call = MethodCall("isQQInstalled", null)
        val mockResult = mock(MethodChannel.Result::class.java)
        plugin.onMethodCall(call, mockResult)

        verify(mockResult).success(false)
    }
}
