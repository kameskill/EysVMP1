package com.example.eysvm.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Rootless host probe. Uses only public SDK APIs and plain file checks.
 * Every result is reported as-is; nothing here tries to bypass restrictions.
 * A missing/denied node means "not visible to this app", not "absent from the phone".
 */
object HostDiagnostics {

    data class Item(val name: String, val value: String)

    fun run(ctx: Context): List<Item> {
        val out = mutableListOf<Item>()
        fun add(n: String, v: String) { out += Item(n, v) }

        // --- Platform ---
        add("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        add("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        add("Fingerprint", Build.FINGERPRINT)
        add("Supported ABIs", Build.SUPPORTED_ABIS.joinToString())
        if (Build.VERSION.SDK_INT >= 31) {
            add("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        add("Hardware", Build.HARDWARE)

        // --- Kernel ---
        try {
            val u = Os.uname()
            add("Kernel", "${u.sysname} ${u.release} ${u.machine}")
            add("Kernel version string", u.version)
        } catch (t: Throwable) { add("Kernel", "unavailable: ${t.message}") }

        // --- Memory / storage / page size ---
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        add("RAM total", "${mi.totalMem / MB} MB")
        add("RAM available", "${mi.availMem / MB} MB")
        val sf = StatFs(Environment.getDataDirectory().path)
        add("Data free", "${sf.availableBytes / MB} MB of ${sf.totalBytes / MB} MB")
        try {
            add("Page size", "${Os.sysconf(OsConstants._SC_PAGESIZE)} bytes")
        } catch (t: Throwable) { add("Page size", "unavailable") }

        // --- SELinux (may be unreadable to apps) ---
        add("SELinux enforce", readOrDenied("/sys/fs/selinux/enforce")
            ?.let { if (it.trim() == "1") "enforcing" else "permissive ($it)" } ?: "not readable by app")
        add("App SELinux context", readOrDenied("/proc/self/attr/current")?.trim() ?: "not readable")

        // --- Namespaces visible to this process ---
        val ns = File("/proc/self/ns").list()?.sorted()?.joinToString()
        add("Namespaces listed", ns ?: "not readable")
        add("Note", "Listing a namespace does not mean the app may unshare/mount. That needs a native probe (next step).")

        // --- Binder ---
        for (p in listOf("/dev/binder", "/dev/hwbinder", "/dev/vndbinder", "/dev/binderfs")) {
            add(p, nodeState(p))
        }

        // --- Hypervisor nodes ---
        for (p in listOf("/dev/kvm", "/dev/gunyah", "/dev/gzvm")) add(p, nodeState(p))

        // --- AVF ---
        val pm = ctx.packageManager
        add("Feature: virtualization_framework",
            pm.hasSystemFeature("android.software.virtualization_framework").toString())
        add("AVF API class present", try {
            Class.forName("android.system.virtualmachine.VirtualMachineManager"); "yes"
        } catch (t: Throwable) { "no" })
        add("AVF note", "Class present does not mean a third-party app may create VMs; " +
            "the create permissions are privileged on stock Android.")

        return out
    }

    private const val MB = 1024L * 1024L

    private fun nodeState(path: String): String {
        val f = File(path)
        return when {
            !f.exists() -> "not visible"
            f.canRead() && f.canWrite() -> "exists, app can read+write"
            f.canRead() -> "exists, read only"
            else -> "exists, no access"
        }
    }

    private fun readOrDenied(path: String): String? =
        try { File(path).readText() } catch (t: Throwable) { null }
}
