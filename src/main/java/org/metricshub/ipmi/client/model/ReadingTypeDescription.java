package org.metricshub.ipmi.client.model;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType.*;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;

/**
 * {@link ReadingType} mapping to match the IPMIUtil event sensor types (states)
 */
public class ReadingTypeDescription {

	private static final String PREDICTIVE_FAILURE = "Predictive failure";

	private ReadingTypeDescription() {
	}

	private static final Map<ReadingType, String> READING_TYPE_TO_DESCRIPTION;

	static {
		Map<ReadingType, String> map = new EnumMap<>(ReadingType.class);

		map.put(FruInactive, "Inactive");
		map.put(SlotConnectorIdentifyStatusAsserted, "Identify Status");
		map.put(HardReset, "Hard reset");
		map.put(FruLatchOpen, "FRU Latch");
		map.put(FruActivationRequested, "Activation Requested");
		map.put(SlotConnectorDeviceInstalled, "Device Installed");
		map.put(WarmReset, "Warm Reset"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(FruActivationInProgress, "Activation in Progress");
		map.put(SlotConnectorReadyForDeviceInstallation, "Ready for Device Installation");
		map.put(PxeBootRequested, "User requested PXE boot");
		map.put(FruActive, "Active");
		map.put(InvalidUsernameOrPassword, "Invalid Username Or Password"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(SlotConnectorReadyForDeviceRemoval, "Ready for Device Removal");
		map.put(AutomaticBootToDiagnostic, "Automatic boot to diagnostic");
		map.put(FruDeactivationRequested, "Deactivation Requested");
		map.put(InvalidPasswordDisable, "Invalid Password Disable"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(EntityPresent, "Device Present");
		map.put(SlotPowerOff, "Slot Power is Off");
		map.put(SoftwareInitiatedHardReset, "OS initiated hard reset");
		map.put(FruDeactivationInProgress, "Deactivation in Progress");
		map.put(SensorFailure, "Sensor failure");
		map.put(EntityAbsent, "Device Absent");
		map.put(SoftwareInitiatedWarmReset, "OS initiated warm reset");
		map.put(FruCommunicationLost, "Communication lost");
		map.put(FruFailure, "FRU failure");
		map.put(SystemRestart, "System Restart");
		map.put(StateDeasserted, "0");
		map.put(SystemFirmwareError, "Firmware Error");
		map.put(StateAsserted, "1");
		map.put(SystemFirmwareHang, "Firmware Hang");
		map.put(SystemFirmwareProgress, "Firmware Progress");
		map.put(HardwareChangeDetected, "Hardware change detected");
		map.put(Frb1BistFailure, "FRB1/BIST failure");
		map.put(FirmwareOrSoftwareChangeDetected, "Firmware or software change detected");
		map.put(Frb2HangInPostFailure, "FRB2/Hang in POST failure");
		map.put(HardwareIncompatibilityDetected, "Hardware incompatibility detected");
		map.put(Frb3ProcessorStartupFailure, "FRB3/Processor startup/init failure");
		map.put(FirmwareOrSoftwareIncompatibilityDetected, "Firmware or software incompatibility detected");
		map.put(DrivePresence, "Drive Present");
		map.put(ConfigurationError, "Configuration Error");
		map.put(InvalidOrUnsupportedHardware, "Invalid or unsupported hardware version");
		map.put(DriveFault, "Drive Fault");
		map.put(UncorrectableCpuComplexError, "SM BIOS Uncorrectable CPU-complex Error");
		map.put(InvalidOrUnsupportedFirmwareOrSoftware, "Invalid or unsupported firmware or software version");
		map.put(PredictiveFailure, PREDICTIVE_FAILURE);
		map.put(ProcessorPresenceDetected, "Presence detected");
		map.put(HotSpare, "Hot Spare");
		map.put(ProcessorDisabled, "Disabled");
		map.put(ConsistencyOrParityCheckInProgress, "Parity Check In Progress");
		map.put(TerminatorPresenceDetected, "Terminator presence detected");
		map.put(SecureModeViolationAttempt, "Secure Mode Violation Attempt"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(OsGracefulStop, "OS graceful stop");
		map.put(InCriticalArray, "In Critical Array");
		map.put(ProcessorAutomaticallyThrottled, "Throttled");
		map.put(PreBootUserPasswordViolation, "Pre-boot password violation - user password");
		map.put(OsGracefulShutdown, "OS graceful shutdown");
		map.put(MachineCheckException, "Machine Check Exception"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(PreBootSetupPasswordViolation, "Pre-boot password violation - setup password");
		map.put(SoftOsShutdown, "PEF initiated soft shutdown");
		map.put(PreBootNetworkPasswordViolation, "Pre-boot password violation - network boot password");
		map.put(LANHeartbeatLost, "Heartbeat Lost");
		map.put(AgentNotResponding, "Agent not responding");
		map.put(OtherPreBootPasswordViolation, "Other pre-boot password violation");
		map.put(LANHeartbeat, "Heartbeat");
		map.put(OutOfBandAccessPasswordViolation, "Out-of-band access password violation");
		map.put(DeviceAbsent, "Device Absent");
		map.put(Parity, "Parity");
		map.put(DevicePresent, "Device Present");
		map.put(MemoryScrubFailed, "Memory Scrub Failed");
		map.put(MemoryDeviceDisabled, "Memory Device Disabled");
		map.put(CorrectableEccOtherCorrectableMemoryErrorLoggingLimitReached, "Correctable ECC logging limit reached");
		map.put(MemoryPresenceDetected, "Presence Detected");
		map.put(MemoryConfigurationError, "Configuration Error");
		map.put(SpareMemoryUnit, "Spare");
		map.put(MemoryAutomaticallyThrottled, "Throttled");
		map.put(MemoryCriticalOvertemperature, "Critical Overtemperature"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(SystemReconfigured, "System Reconfigured");
		map.put(OemSystemBootEvent, "OEM System boot event");
		map.put(ABootCompleted, "A: boot completed");
		map.put(UndeterminedSystemHardwareFailure, "Undetermined system hardware failure");
		map.put(TransitionToRunning, "Transition to Running");
		map.put(CBootCompleted, "C: boot completed");
		map.put(EntryAddedToAuxiliaryLog, "Entry added to auxiliary log");
		map.put(TransitionToInTest, "Transition to In Test");
		map.put(PxeBootCompleted, "PXE boot completed");
		map.put(PefAction, "PEF Action");
		map.put(TransitionToPowerOff, "Transition to Power Off");
		map.put(DiagnosticBootCompleted, "Diagnostic boot completed");
		map.put(TimestampClockSynch, "Timestamp Clock Sync");
		map.put(TransitionToOnLine, "Transition to On Line");
		map.put(CdRomBootCompleted, "CD-ROM boot completed");
		map.put(BootSourceSelectionTimeout, "Timeout waiting for selection");
		map.put(PowerSupplyConfigurationError, "Config Error");
		map.put(TransitionToOffLine, "Transition to Off Line");
		map.put(TimerExpired, "Timer expired");
		map.put(RomBootCompleted, "ROM boot completed");
		map.put(TransitionToOffDuty, "Transition to Off Duty");
		map.put(TimerHardReset, "Hard reset");
		map.put(BootCompleted, "boot completed - device not specified");
		map.put(PredictiveFailureDeasserted, "Predictive Failure Deasserted");
		map.put(TransitionToDegraded, "Transition to Degraded");
		map.put(TimerPowerDown, "Power down");
		map.put(AcpiS5EnteredByOverride, "S5: entered by override");
		map.put(LANLeashLost, "Leash Lost"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(Predictive, "Predictive Failure Asserted");
		map.put(TransitionToPowerSave, "Transition to Power Save");
		map.put(TimerPowerCycle, "Power cycle");
		map.put(AcpiLegacyOnState, "Legacy ON state");
		map.put(PowerOffOrDown, "Power off/down");
		map.put(UnauthorizedDock, "Unauthorized dock");
		map.put(InstallError, "Install Error");
		map.put(AcpiLegacyOffState, "Legacy OFF state");
		map.put(PowerCycle, "Power cycle");
		map.put(FANAreaIntrusion, "FAN area intrusion");
		map.put(FullyRedundant, "Fully Redundant");
		map.put(CableInterconnectConnected, "Connected");
		map.put(PowerDown240V, "240VA power down");
		map.put(RedundancyLost, "Redundancy Lost");
		map.put(AcpiUnknown, "Unknown");
		map.put(CableInterconnectConfigurationError, "Config Error");
		map.put(InterlockPowerDown, "Interlock power down");
		map.put(RedundancyDegraded, "Redundancy Degraded");
		map.put(BatteryLow, "Low");
		map.put(PowerInputLost, "AC lost");
		map.put(NonRedundant_SufficientResourcesFromRedundant, "Non-Redundant: Sufficient from Redundant");
		map.put(BatteryFailed, "Failed");
		map.put(PowerUnitSoftPowerControlFailure, "Soft-power control failure");
		map.put(BatteryPresenceDetected, "Presence Detected");
		map.put(PowerUnitFailure, "Failure detected");
		map.put(PowerUnitPredictiveFailure, PREDICTIVE_FAILURE);
		map.put(BusFatalError, "Bus Fatal Error");
		map.put(CorrectableMemoryErrorLoggingDisabled, "Correctable memory error logging disabled");
		map.put(BusDegraded, "Bus Degraded"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(EventTypeLoggingDisabled, "Event logging disabled");
		map.put(LogAreaReset, "Log area reset/cleared");
		map.put(TransitionToOK, "Transition to OK");
		map.put(AllEventLoggingDisabled, "All event logging disabled");
		map.put(TransitionToNonCriticalFromOK, "Transition to Non-critical from OK");
		map.put(FruServiceRequestButtonPressed, "FRU Service");
		map.put(SelFull, "Log full"); // Event Log
		map.put(TransitionToCriticalFromLessSevere, "Transition to Critical from less severe");
		map.put(SelAlmostFull, "Log almost full");
		map.put(TransitionToNonRecoverableFromLessSevere, "Transition to Non-recoverable from less severe");
		map.put(CorrectableMachineCheckErrorLoggingDisabled, "Correctable Machine Check Error Logging Disabled"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(TransitionToNonCriticalFromMoreSevere, "Transition to Non-critical from more severe");
		map.put(TransitionToCriticalFromNonRecoverable, "Transition to Critical from Non-recoverable");
		map.put(SlotConnectorDeviceRemovalRequest, "Device Removal Request");
		map.put(TransitionToNonRecoverable, "Transition to Non-recoverable");
		map.put(EntityDisabled, "Device Disabled");
		map.put(InterlockAsserted, "Interlock");
		map.put(Monitor, "Monitor");
		map.put(SlotDisabled, "Slot is Disabled");
		map.put(SlotHoldsSpareDevice, "Spare Device");
		map.put(PlatformGeneratedPage, "Platform generated page");
		map.put(PlatformGeneratedLanAlert, "Platform generated LAN alert");
		map.put(PlatformEventTrapGenerated, "Platform Event Trap generated");
		map.put(PlatformGeneratedSnmpTrap, "Platform generated SNMP trap, OEM format");
		map.put(SensorAccessUnavailable, "Sensor access degraded or unavailable");
		map.put(ControllerAccessUnavailable, "Controller access degraded or unavailable");
		map.put(ManagementControllerOffLine, "Management controller off-line");
		map.put(SuccessfulHardwareChangeDetected, "Hardware change success");
		map.put(ManagementControllerUnavailable, "Management controller unavailable");
		map.put(SuccessfulSoftwareOrFWChangeDetected, "Firmware or software change success");
		map.put(InFailedArray, "In Failed Array");
		map.put(RebuildRemapInProgress, "Rebuild In Progress");
		map.put(CorrectableMachineCheckError, "Correctable Machine Check Error"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(RebuildRemapAborted, "Rebuild Aborted");
		map.put(Ierr, "IERR");
		map.put(ChipsetSoftPowerControlFailure, "Soft-power control failure");
		map.put(ProcessorThermalTrip, "Thermal Trip");
		map.put(ChipsetThermalTrip, "Thermal Trip");
		map.put(D0PowerState, "D0 Power State");
		map.put(D1PowerState, "D1 Power State");
		map.put(D2PowerState, "D2 Power State");
		map.put(D3PowerState, "D3 Power State");
		map.put(CriticalStopDuringOsLoad, "Error during system startup");
		map.put(RunTimeCriticalStop, "Run-time critical stop");
		map.put(DeviceDisabled, "Device Disabled");
		map.put(DeviceEnabled, "Device Enabled");
		map.put(CorrectableEcc, "Correctable ECC");
		map.put(UncorrectableECC, "Uncorrectable ECC");
		map.put(LimitNotExceeded, "Limit Not Exceeded");
		map.put(LimitExceeded, "Limit Exceeded");
		map.put(AcpiS0G0Working, "S0/G0: working");
		map.put(AcpiS1SleepingProcessorContextMaintained, "S1: sleeping with system hw & processor context maintained");
		map.put(PowerSupplyPresenceDetected, "Presence detected");
		map.put(AcpiS2SleepingProcessorContextLost, "S2: sleeping, processor context lost");
		map.put(PowerSupplyFailureDetected, "Failure detected");
		map.put(TimerInterrupt, "Timer interrupt");
		map.put(AcpiS3SleepingProcessorContextLostMemoryRetained, "S3: sleeping, processor & hw context lost, memory retained");
		map.put(PowerSupplyPredictiveFailure, PREDICTIVE_FAILURE);
		map.put(NonRedundant_SufficientResourcesFromInsufficientResources, "Non-Redundant: Sufficient from Insufficient");
		map.put(AcpiS4NonVolatileSleep, "S4: non-volatile sleep/suspend-to-disk");
		map.put(PowerSupplyInputLost, "Power Supply AC lost");
		map.put(NonRedundant_InsufficientResources, "Non-Redundant: Insufficient Resources");
		map.put(AcpiS5G2SoftOff, "S5/G2: soft-off");
		map.put(PowerSupplyInputLostOrOutOfRange, "AC lost or out-of-range");
		map.put(RedundancyDegradedFromFullyRedundant, "Redundancy Degraded from Fully Redundant");
		map.put(PerformanceMet, "Performance Met");
		map.put(AcpiS4S5SoftOffStateUndetermined, "S4/S5: soft-off");
		map.put(PowerSupplyInputOutOfRange, "AC out-of-range, but present");
		map.put(RedundancyDegradedFromNonRedundant, "Redundancy Degraded from Non-Redundant");
		map.put(PerformanceLags, "Performance Lags");
		map.put(AcpiG3MechanicalOff, "G3: mechanical off");
		map.put(AcpiSleepingInS1S2OrS3, "Sleeping in S1/S2/S3 state");
		map.put(AcpiG1Sleeping, "G1: sleeping");
		map.put(FrontPanelInterrupt, "NMI/Diag Interrupt");
		map.put(Informational, "Informational");
		map.put(NoBootableMedia, "No bootable media");
		map.put(BusTimeout, "Bus Timeout");
		map.put(NonBootableDisketteLeftInDrive, "Unrecoverable diskette failure");
		map.put(IoChannelCheckNmi, "I/O Channel check NMI");
		map.put(PxeServerNotFound, "PXE server not found");
		map.put(Software, Software.name());
		map.put(InvalidBootSector, "Invalid boot sector");
		map.put(PciPErr, "PCI PERR");
		map.put(GeneralChassisIntrusion, "General Chassis intrusion");
		map.put(TransitionToIdle, "Transition to Idle");
		map.put(PciSErr, "PCI SERR");
		map.put(DriveBayIntrusion, "Drive Bay intrusion");
		map.put(TransitionToActive, "Transition to Active");
		map.put(EisaFailSafeTimeout, "EISA failsafe timeout");
		map.put(IoCardAreaIntrusion, "I/O Card area intrusion");
		map.put(TransitionToBusy, "Transition to Busy");
		map.put(BusCorrectableError, "Bus Correctable error");
		map.put(ProcessorAreaIntrusion, "Processor area intrusion");
		map.put(BusUncorrectableError, "Bus Uncorrectable error");
		map.put(FatalNmi, "Fatal NMI");
		map.put(PowerButtonPressed, "Power Button pressed");
		map.put(SleepButtonPressed, "Sleep Button pressed");
		map.put(FruNotInstalled, "Not Installed");
		map.put(SlotConnectorFaultStatusAsserted, "Fault Status");
		map.put(PowerUp, "Power up"); // No correspondence found in ipmiTool code - Update MS_HW_IpmiTool.hdf (Awk)
		map.put(ResetButtonPressed, "Reset Button pressed");
		map.put(Unknown, Unknown.name());

		READING_TYPE_TO_DESCRIPTION = Collections.unmodifiableMap(map);
	}

	/**
	 * @param readingType The {@link ReadingType} key
	 * @return {@link String} value extracted from the internal lookup matching the output of IPMIUtil and the java verax IPMI lib
	 */
	public static String getReadingType(ReadingType readingType) {
		return READING_TYPE_TO_DESCRIPTION.get(readingType);
	}
}
