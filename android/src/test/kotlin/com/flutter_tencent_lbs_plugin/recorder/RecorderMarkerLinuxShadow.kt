package com.flutter_tencent_lbs_plugin.recorder

import android.system.ErrnoException
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowLinux

/**
 * Robolectric 4.16.1's ShadowLinux.open maps every IOException to EIO.
 * Supply ENOENT only for a missing recorder marker opened read-only; all other
 * opens retain Robolectric's behavior. The existence check simulates the state
 * at open, and the hook can create the marker immediately after that instant.
 */
@Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
class RecorderMarkerLinuxShadow : ShadowLinux() {
    @Implementation
    override fun open(path: String, flags: Int, mode: Int): FileDescriptor {
        if (File(path).name != "tencent_location_recording_failure.txt" || flags != OsConstants.O_RDONLY) {
            return super.open(path, flags, mode)
        }
        val marker = File(path)
        if (!marker.exists()) {
            createOnMissingOpen?.let {
                createOnMissingOpen = null
                marker.writeText(it)
            }
            throw ErrnoException("open", OsConstants.ENOENT)
        }
        return super.open(path, flags, mode).also { openedDescriptors.add(it) }
    }

    companion object {
        var createOnMissingOpen: String? = null
        val openedDescriptors = mutableListOf<FileDescriptor>()

        @JvmStatic
        @Resetter
        fun reset() {
            // Close any leaked descriptor after the test's assertions report it.
            openedDescriptors.filter { it.valid() }.forEach { FileInputStream(it).close() }
            openedDescriptors.clear()
            createOnMissingOpen = null
        }
    }
}
