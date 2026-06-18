/* EPD controller for Nook Glowlight 4 Plus (bnrv1300, "Emperor" platform, Android 8.1).
 *
 * Full refresh (GC16):
 *   Writes 4 (EINK_GC16_MODE) to the AllWinner EPD sysfs node:
 *     /sys/devices/virtual/disp/disp/waveform/force_update_mode
 *   This node is chmod 666 on boot by the epd_gc16 Magisk module.
 *   The driver adds EINK_GAMMA_CORRECT (0x200000) and uses the GC16 LUT.
 *
 *   Confirmed via dmesg: writing 4 changes kernel EPD mode from 0x200084 (GU16)
 *   to 0x200004 (GC16) on the next display update.
 *
 *   Surface.einkChangeQuickUpdateMode and view.invalidate(int) were tried first but
 *   do not reach the AllWinner kernel driver from a non-system app.
 *
 * Partial/fast modes:
 *   Resets force_update_mode to 0 so the driver uses its default GU16 waveform.
 *
 * needsView() = false: NativeSurfaceView causes a surface setup crash on Emperor hardware.
 *
 * Constants from sunxi-kobo.h / EpdDisplayControllerImpl.
 * see https://github.com/koreader/koreader/issues/14574
 */

package org.koreader.launcher.device.epd

import android.util.Log
import java.io.File
import java.io.IOException
import java.util.*
import org.koreader.launcher.device.EPDInterface
import org.koreader.launcher.device.epd.freescale.NTXEPDController

class NookEmperorEPDController : EPDInterface {

    companion object {
        private const val TAG = "EPD"

        const val EMPEROR_EINK_GC16_MODE = 0x04
        const val EMPEROR_EINK_GU16_MODE = 0x84
        const val EMPEROR_EINK_NO_MERGE = Integer.MIN_VALUE // 0x80000000

        private const val FORCE_UPDATE_MODE =
            "/sys/devices/virtual/disp/disp/waveform/force_update_mode"
    }

    override fun getPlatform(): String = "freescale"
    override fun getMode(): String = "all"

    override fun getWaveformFull(): Int      = EMPEROR_EINK_NO_MERGE + EMPEROR_EINK_GC16_MODE
    override fun getWaveformPartial(): Int   = EMPEROR_EINK_GU16_MODE
    override fun getWaveformFullUi(): Int    = EMPEROR_EINK_NO_MERGE + NTXEPDController.EINK_WAVEFORM_MODE_GLR16
    override fun getWaveformPartialUi(): Int = EMPEROR_EINK_GU16_MODE
    override fun getWaveformFast(): Int      = EMPEROR_EINK_GU16_MODE

    override fun getWaveformDelay(): Int     = 0
    override fun getWaveformDelayUi(): Int   = 0
    override fun getWaveformDelayFast(): Int = 0

    override fun needsView(): Boolean = false

    override fun setEpdMode(
        targetView: android.view.View,
        mode: Int, delay: Long,
        x: Int, y: Int, width: Int, height: Int, epdMode: String?
    ) {
        val forceMode = if (mode == getWaveformFull()) EMPEROR_EINK_GC16_MODE else 0
        try {
            File(FORCE_UPDATE_MODE).writeText(forceMode.toString())
            Log.i(TAG, String.format(Locale.US,
                "Emperor EPD: force_update_mode=%d (waveform=0x%x)", forceMode, mode))
        } catch (e: IOException) {
            Log.w(TAG, "Emperor EPD: sysfs write failed (chmod 666 needed?): $e")
        }
    }

    override fun resume() {
        // Reset to no-force on resume so partial refreshes use the default GU16 waveform.
        try {
            File(FORCE_UPDATE_MODE).writeText("0")
        } catch (_: IOException) {}
    }

    override fun pause() {}
}
