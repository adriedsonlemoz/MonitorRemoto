package br.dev.monitorremoto.core

import org.junit.Assert.*
import org.junit.Test

class RemoteManagePolicyTest {
    @Test fun operationsMustBeExplicitAndConfined() {
        assertTrue(RemoteManagePolicy.allowed("mkdir", emptyList(), name = "Clipes"))
        assertTrue(RemoteManagePolicy.allowed("rename", listOf("clipe.mp4"), name = "novo.mp4"))
        assertTrue(RemoteManagePolicy.allowed("move", listOf("clipe.mp4"), listOf("Clipes")))
        assertTrue(RemoteManagePolicy.allowed("trash", listOf("clipe.mp4")))
        assertFalse(RemoteManagePolicy.allowed("delete", emptyList()))
        assertFalse(RemoteManagePolicy.allowed("rename", emptyList(), name = "arquivo"))
        assertFalse(RemoteManagePolicy.allowed("shell", listOf("clipe.mp4")))
        assertFalse(RemoteManagePolicy.allowed("move", listOf("Clipes"), listOf("Clipes", "Sub")))
        assertFalse(RemoteManagePolicy.allowed("copy", listOf("Clipes"), listOf("Clipes")))
        assertFalse(RemoteManagePolicy.allowed("mkdir", emptyList(), name = "../fuga"))
        assertFalse(RemoteManagePolicy.allowed("move", listOf("a"), listOf("../b")))
    }
}
