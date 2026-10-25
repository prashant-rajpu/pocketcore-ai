package com.ai.pocketcore.data.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationHelperTest {

    @Test
    fun formatBytesFormatsProperUnits() {
        assertEquals("0 B", DownloadNotificationHelper.formatBytes(0L))
        assertEquals("0 B", DownloadNotificationHelper.formatBytes(-50L))
        assertEquals("512.00 B", DownloadNotificationHelper.formatBytes(512L))
        assertEquals("1.00 KB", DownloadNotificationHelper.formatBytes(1024L))
        assertEquals("1.50 MB", DownloadNotificationHelper.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("4.58 GB", DownloadNotificationHelper.formatBytes(4_920_000_000L))
    }

    @Test
    fun formatSpeedFormatsMbPerSecond() {
        assertEquals("0.0 MB/s", DownloadNotificationHelper.formatSpeed(0.0))
        assertEquals("0.0 MB/s", DownloadNotificationHelper.formatSpeed(-1.5))
        assertEquals("12.4 MB/s", DownloadNotificationHelper.formatSpeed(12.38))
        assertEquals("105.0 MB/s", DownloadNotificationHelper.formatSpeed(105.02))
    }

    @Test
    fun formatEtaHandlesSecondsMinutesHours() {
        assertEquals("--", DownloadNotificationHelper.formatEta(-1L))
        assertEquals("45s", DownloadNotificationHelper.formatEta(45L))
        assertEquals("2m 15s", DownloadNotificationHelper.formatEta(135L))
        assertEquals("1h 2m", DownloadNotificationHelper.formatEta(3725L))
    }
}
