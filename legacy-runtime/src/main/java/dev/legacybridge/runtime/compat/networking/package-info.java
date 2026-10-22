/**
 * network-snapshot-v1 uses the shared SnapshotContractAdapter OFFLINE operation:
 * original reviewed networking bodies remain and call the native target APIs.
 * No empty runtime shim, old Forge copy or general packet translation is installed.
 * All other protocols and server-mutating packets require independent recipes.
 */
package dev.legacybridge.runtime.compat.networking;
