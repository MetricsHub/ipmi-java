package org.metricshub.ipmi.client;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.metricshub.ipmi.client.model.DeviceDescription;
import org.metricshub.ipmi.client.model.Fru;
import org.metricshub.ipmi.client.model.FruDevice;
import org.metricshub.ipmi.client.model.Sensor;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;

public class IpmiResultConverter {

	private static final double NO_READING = 255d;

	private static final DoubleFunction<Double> IDENTITY_FUNCTION = v -> v;
	private static final DoubleFunction<Double> VOLTAGE_CONVERSION_FUNCTION = v -> v * 1000;
	private static final DoubleFunction<Double> FAHRENHEIT_TO_CELSIUS_CONVERSION_FUNCTION = v -> (v - 32.0) * 0.5556;
	private static final DoubleFunction<Double> KELVIN_TO_CELSIUS_CONVERSION_FUNCTION = v -> v - 273.15;

	private IpmiResultConverter() {
	}

	/**
	 * Convert the given List of FRUs and Sensors to a {@link String} result (semicolon-separated values)
	 *
	 * @param frus    The list of Field Replaceable Units (FRU) we wish to format as the following: <br>
	 *                <em>FRU;$vendor;$model;$serialNumber</em>
	 * @param sensors The list of sensor records (Compact or Full) we wish to format as the following: <br>
	 *                <ul>
	 *                  <li><b>States:</b>
	 *                    <em>$deviceType;$deviceId;$deviceUniqueId;$vendor;$model;$serialNumber;$sensorName=$state|$sensorName=$state...</em>
	 *                  </li>
	 *                 <li><b>Readings:</b>
	 *                   <ul>
	 *                     <li><em>Temperature;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em></li>
	 *                     <li><em>Voltage;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em></li>
	 *                     <li><em>Fan;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em></li>
	 *                     <li><em>Current;$sensorId;$sensorName;$sensorUniqueId;$value</em></li>
	 *                     <li><em>PowerConsumption;$sensorId;$sensorName;$sensorUniqueId;$value</em></li>
	 *                     <li><em>Energy;$sensorId;$sensorName;$sensorUniqueId;$value</em></li>
	 *                   </ul>
	 *                 </li>
	 *                </ul>
	 * @return String value
	 */
	public static final String convertResult(final List<Fru> frus, final List<Sensor> sensors) {

		// FRU id to FRU Device used when processing the sensors
		Map<String, FruDevice> frusLookup = new HashMap<>();

		// Process the FRUs
		Stream<String> fruRecords = processFrus(frus, frusLookup);

		// Process the Sensors
		Stream<String> sensorStates = processSensorStates(sensors, frusLookup);

		// Process the sensor readings
		Stream<String> sensorReadings = processSensorReadings(sensors);

		return Stream.of(fruRecords, sensorStates, sensorReadings)
			.flatMap(Function.identity())
			.collect(Collectors.joining("\n"));
	}

	/**
	 * Process the given sensors. Extracts only the full sensors then handle each full sensor to extract the reading value. E.g. PowerConsumption
	 *
	 * @param sensors The sensor list we wish to process
	 * @return The stream of the sensor readings
	 */
	private static Stream<String> processSensorReadings(final List<Sensor> sensors) {

		// Parse each full sensor as it should define the reading value and
		return sensors.stream()
			.filter(Sensor::isFull)
			.filter(sensor -> sensor.getData() != null && sensor.getRecord() != null)
			.map(IpmiResultConverter::extractFullSensorReadingValue)
			.filter(Objects::nonNull);
	}

	/**
	 * Handle a Full sensor to extract the reading value (temperature, voltage, Fan speed, current, power consumption, energy).
	 *
	 * @param fullSensor The full sensor we wish to process
	 * @return The sensor reading as semicolon-separated values. E.g. Temperature;0001;Ambient Temp;Front Panel Board 1;22.0;38;41
	 */
	private static String extractFullSensorReadingValue(final Sensor fullSensor) {

		// Get data and record, never null
		GetSensorReadingResponseData data = fullSensor.getData();
		FullSensorRecord fullRecord = (FullSensorRecord) fullSensor.getRecord();

		// Read the various fields of the returned table
		String sensorId = String.format("%04x", fullRecord.getId());

		// Get the type of device as expected by the AWK script in the MS_HW_IpmiTool.hdfs
		String deviceType = DeviceDescription.getDeviceType(fullRecord.getEntityId());

		// The entity instance number
		byte deviceId = fullRecord.getEntityInstanceNumber();

		// Get the sensor name
		String sensorName = fullRecord.getName();

		// Get the unit
		SensorUnit unit = fullRecord.getSensorBaseUnit();

		// No Reading ? Skip.
		if (data.getPlainSensorReading() == NO_READING || deviceType == null || unit == null || sensorName == null) {
			return null;
		}

		// Sensor reading value. E.g temperature, power consumption, ...
		double value = data.getSensorReading(fullRecord);

		// unique id
		String sensorUniqueId = buildDeviceUniqueId(deviceType, deviceId);

		// Which unit?
		switch (unit) {
		case DegreesC:
		case DegreesF:
		case DegreesK:
			return temperatureRow(fullRecord, sensorId, sensorName, value, unit, sensorUniqueId);
		case Volts:
			return voltageRow(fullRecord, sensorId, sensorName, value, sensorUniqueId);
		case Rpm:
			return fanSpeedRow(fullRecord, sensorId, sensorName, value, sensorUniqueId);
		case Amps:
			return currentRow(sensorId, sensorName, value, sensorUniqueId);
		case Watts:
			return powerConsumptionRow(sensorId, sensorName, value, sensorUniqueId);
		case Joules:
			return energyRow(sensorId, sensorName, value, sensorUniqueId);
		default:
			return null;
		}
	}


	/**
	 * Get the energy row using the given fields.
	 *
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return The energy sensor reading record formatted as <em>Energy;$sensorId;$sensorName;$sensorUniqueId;$value</em>
	 */
	private static String energyRow(final String sensorId, final String sensorName,
			final double value, final String sensorUniqueId) {
		return String.join(";", "Energy", sensorId, sensorName, sensorUniqueId, String.valueOf(value));
	}

	/**
	 * Get the power consumption row using the given fields.
	 *
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return The power consumption sensor reading record formatted as: <em>PowerConsumption;$sensorId;$sensorName;$sensorUniqueId;$value</em>
	 */
	private static String powerConsumptionRow(final String sensorId, final String sensorName,
			final 	double value, final  String sensorUniqueId) {
		return String.join(";", "PowerConsumption", sensorId, sensorName, sensorUniqueId, String.valueOf(value));
	}

	/**
	 * Get the current row reading using the given fields.
	 *
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return Current sensor reading record formatted as: <em>Current;$sensorId;$sensorName;$sensorUniqueId;$value</em>
	 */
	private static String currentRow(final String sensorId, final String sensorName,
			final double value, final String sensorUniqueId) {
		return String.join(";", "Current", sensorId, sensorName, sensorUniqueId, String.valueOf(value));
	}

	/**
	 * Get the fan speed row using the given fields.
	 *
	 * @param sensor         The full sensor record used to extract the threshold 1 and threshold 2.
	 *                       <ol>
	 *                       <li><b>Threshold 1</b> is the <em>LowerCriticalThreshold</em> otherwise we get the
	 *                       <em>LowerNonRecoverableThreshold</em>.</li>
	 *                       <li><b>Threshold 2</b> is the <em>LowerNonCriticalThreshold</em></li>
	 *                       </ol>
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return The Fan speed sensor reading record formatted as: Fan;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em>
	 */
	private static String fanSpeedRow(final FullSensorRecord sensor, final String sensorId,
			final String sensorName, final double value, final String sensorUniqueId) {

		String threshold1 = getAvailableThreshold(IDENTITY_FUNCTION, sensor.getLowerCriticalThreshold(), sensor.getLowerNonRecoverableThreshold());

		String threshold2 = getThresholdValue(IDENTITY_FUNCTION, sensor.getLowerNonCriticalThreshold());

		return String.join(";", "Fan", sensorId, sensorName, sensorUniqueId, String.valueOf(value), threshold1, threshold2);
	}

	/**
	 * Get the voltage row using the given fields.
	 *
	 * @param sensor         The full sensor record used to extract the threshold 1 and threshold 2.
	 *                       <ol>
	 *                       <li><b>Threshold 1</b> is the <em>LowerNonCriticalThreshold</em> otherwise we get the
	 *                       <em>LowerCriticalThreshold</em>.</li>
	 *                       <li><b>Threshold 2</b> is the <em>UpperNonCriticalThreshold</em> otherwise we get the
	 *                       <em>UpperCriticalThreshold</em>, if both thresholds are not available then we get the
	 *                       <em>UpperNonRecoverableThreshold</em></li>
	 *                       </ol>
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return Voltage sensor reading record formatted as: <em>Voltage;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em>
	 */
	private static String voltageRow(final FullSensorRecord sensor, final String sensorId,
			final String sensorName, final double value, final String sensorUniqueId) {

		String threshold1 = getAvailableThreshold(VOLTAGE_CONVERSION_FUNCTION, sensor.getLowerNonCriticalThreshold(), sensor.getLowerCriticalThreshold(),
				sensor.getLowerNonRecoverableThreshold());

		String threshold2 = getAvailableThreshold(VOLTAGE_CONVERSION_FUNCTION, sensor.getUpperNonCriticalThreshold(), sensor.getUpperCriticalThreshold(),
				sensor.getUpperNonRecoverableThreshold());

		return String.join(";", "Voltage", sensorId, sensorName, sensorUniqueId, String.valueOf(value * 1000), threshold1, threshold2);
	}

	/**
	 * Get the temperature row using the given fields.
	 *
	 * @param sensor         The full sensor record used to extract the threshold 1 and threshold 2.
	 *                       <ol>
	 *                       <li><b>Threshold 1</b> is the <em>UpperNonCriticalThreshold</em>.</li>
	 *                       <li><b>Threshold 2</b> is the <em>UpperCriticalThreshold</em> otherwise we get the
	 *                       <em>UpperNonRecoverableThreshold</em></li>
	 *                       </ol>
	 * @param sensorId       The id of the sensor
	 * @param sensorName     The name of the sensor
	 * @param value          The value to set, always converted to Degrees Celsius
	 * @param unit           The unit used to convert Fahrenheit to Celsius or Kelvin to Celsius
	 * @param sensorUniqueId The sensor unique id ($type $deviceId)
	 * @return Temperature sensor reading record formatted as: <em>Temperature;$sensorId;$sensorName;$sensorUniqueId;$value;$threshold1;$threshold2</em>
	 */
	private static String temperatureRow(final FullSensorRecord sensor, final String sensorId,
			final String sensorName, double value, final SensorUnit unit, final String sensorUniqueId) {

		DoubleFunction<Double> conversionFunction = IDENTITY_FUNCTION;
		if (SensorUnit.DegreesF.equals(unit)) {
			// Convert Fahrenheit to Celsius
			conversionFunction = FAHRENHEIT_TO_CELSIUS_CONVERSION_FUNCTION;
		} else if (SensorUnit.DegreesK.equals(unit)) {
			// Convert Kelvin to Celsius
			conversionFunction = KELVIN_TO_CELSIUS_CONVERSION_FUNCTION;
		}

		String threshold1 = getThresholdValue(conversionFunction, sensor.getUpperNonCriticalThreshold());
		String threshold2 = getAvailableThreshold(conversionFunction, sensor.getUpperCriticalThreshold(), sensor.getUpperNonRecoverableThreshold());

		value = conversionFunction.apply(value);

		return String.join(";", "Temperature", sensorId, sensorName, sensorUniqueId, String.valueOf(value), threshold1, threshold2);
	}

	/**
	 * Process the sensor states
	 *
	 * @param sensors        The sensor list we wish to process
	 * @param frusLookup     The FRUs lookup used to extract vendor, model and serial number
	 * @return The stream of sensor records including sensor states
	 */
	private static Stream<String> processSensorStates(final List<Sensor> sensors, final Map<String, FruDevice> frusLookup) {

		Map<String, String> sensorEntries = new LinkedHashMap<>();

		sensors.forEach(sensor -> extractSensorStates(sensor, frusLookup, sensorEntries));

		return sensorEntries.values().stream();
	}

	/**
	 * Extract the sensor state and format the result as the following: <br>
	 * <em>$deviceType;$deviceId;$deviceUniqueId;$vendor;$model;$serialNumber;$sensorName=$state|$sensorName=$state...</em>
	 *
	 * @param sensor        The sensor we wish to extract its states
	 * @param frusLookup    The FRUs lookup that should contain sensor metadata, vendor, model and serial number
	 * @param sensorEntries The sensor entries used to append existing device states
	 */
	private static void extractSensorStates(final Sensor sensor, final Map<String, FruDevice> frusLookup, final Map<String, String> sensorEntries) {
		// Get the sensor states
		String states = sensor.getStates();

		// Get the type of device as expected by the AWK script in the MS_HW_IpmiTool.hdfs
		String deviceType = DeviceDescription.getDeviceType(sensor.getEntityId());

		// The entity instance number
		Byte deviceId = sensor.getDeviceId();

		// Bypass sensors with no state asserted
		if (Utils.isBlank(states) || states.toLowerCase().contains("=device absent") || deviceId == null || Utils.isBlank(deviceType)) {
			return;
		}

		// Use this id to retrieve the corresponding FRU
		String deviceUniqueId = buildDeviceUniqueId(deviceType, deviceId);

		String entry;
		// Check whether this sensor entry was already discovered so present in the local map
		if (sensorEntries.containsKey(deviceUniqueId)) {
			entry = sensorEntries.get(deviceUniqueId);
			entry += "|" + states;
		} else {
			// So, it's the first time we meet this sensor entry, look up its FRU entry
			FruDevice fru = frusLookup.get(deviceUniqueId);
			String vendor = Utils.EMPTY;
			String model = Utils.EMPTY;
			String serialNumber = Utils.EMPTY;

			// Ok we have the corresponding FRU, let's extract the vendor, model, and serialNumber
			if (fru != null) {
				vendor = Utils.getValueOrEmpty(fru.getVendor());
				model = Utils.getValueOrEmpty(fru.getModel());
				serialNumber = Utils.getValueOrEmpty(fru.getSerialNumber());
			}

			// Add all of this to the device list
			entry = String.join(";", deviceType, deviceId.toString(), deviceUniqueId, vendor, model, serialNumber, states);
		}

		// Add the entry to the local lookup
		sensorEntries.put(deviceUniqueId, entry);
	}

	/**
	 * Process the given list of FRUs
	 *
	 * @param frus           The list of Field Replaceable Units (FRU) we wish to format as the following: <br>
	 *                       <em>FRU;$vendor;$model;$serialNumber</em>
	 * @param frusLookup     The frusLookup used to store the FRUs indexed by the unique identifier in order to easily fetch them at the sensors
	 *                       processing step.
	 * @return The stream of the FRU record including very good, good and poor FRUs
	 */
	private static Stream<String> processFrus(final List<Fru> frus, final Map<String, FruDevice> frusLookup) {

		// Very Good FRU list defines the Board/Chassis FRUs with ProductInfo data containing model and serial number
		LinkedList<FruDevice> veryGoodFruList = new LinkedList<>();

		// Good FRU list defines the Panel FRUs with ProductInfo data containing model and serial number
		LinkedList<FruDevice> goodFruList = new LinkedList<>();

		// Poor FRU list defines other FRUs
		LinkedList<FruDevice> poorFruList = new LinkedList<>();

		// Loop over each collected FRU and try to extract the model, vendor and serial number
		for (Fru fru : frus) {
			FruDeviceLocatorRecord fruLocator = fru.getFruLocator();
			if (fruLocator == null) {
				continue;
			}

			// Get the entityId value. It is the type of the device
			EntityId entityId = EntityId.parseInt(fruLocator.getFruEntityId());

			// Build the FRU unique id
			String deviceUniqueId = buildDeviceUniqueId(DeviceDescription.getDeviceType(entityId), fruLocator.getFruEntityInstance());

			FruDevice fruDevice;

			ProductInfo productInfo = getInfo(fru.getFruRecords(), ProductInfo.class);

			// Product information, usually very good if attached to a system entity
			if (hasValidProductInfo(productInfo)) {
				fruDevice = createFruDeviceFromProductInfo(veryGoodFruList, goodFruList, poorFruList, entityId, productInfo);
			} else {
				// And then, board information: most often incomplete stuff
				// But if we have that only, we'll try to make something of it
				fruDevice = createFruDeviceFromBoardInfo(poorFruList, fru);
			}

			if (fruDevice != null) {
				// Anyway, add what we found to the temporary internal FRU lookup that we will re-use later
				// to complement the sdr data
				frusLookup.put(deviceUniqueId, fruDevice);
			}
		}

		// Return good and poor FRU list
		return Stream.of(veryGoodFruList, goodFruList, poorFruList)
				.flatMap(Collection::stream)
				.map(FruDevice::toString);
	}

	/**
	 * Create the device unique id, concatenate the deviceType and the instanceId using the whitespace separator
	 *
	 * @param deviceType The type of the device we are building its unique id
	 * @param instanceId The instance id of the device. e.g. 1 for 'Fan 1', 2, for 'Fan 2'
	 * @return String value
	 */
	private static String buildDeviceUniqueId(final String deviceType, final int instanceId) {
		return String.format("%s %d", deviceType, instanceId);
	}

	/**
	 * Create the intermediary {@link FruDevice} object from the {@link BoardInfo} located in the {@link Fru} instance.
	 *
	 * @param poorFruList Poor FRU list to update
	 * @param fru         The {@link Fru} instance from which we extract the {@link BoardInfo} which gives us some information about the model,
	 *                    vendor and serial
	 * @return a new instance of {@link FruDevice} or <code>null</code> if the vendor or the model cannot be collected
	 */
	private static FruDevice createFruDeviceFromBoardInfo(final LinkedList<FruDevice> poorFruList, final Fru fru) {

		BoardInfo boardInfo = getInfo(fru.getFruRecords(), BoardInfo.class);

		// Strange but make sure the board information is here
		if (boardInfo == null) {
			return null;
		}

		String vendor = Utils.getValueOrEmpty(boardInfo.getBoardManufacturer());
		String model = Utils.getValueOrEmpty(boardInfo.getBoardProductName());
		String serialNumber = Utils.getValueOrEmpty(boardInfo.getBoardSerialNumber());
		String partNumber = Utils.getValueOrEmpty(boardInfo.getBoardPartNumber());

		// Combine serialNumber and partNumber
		serialNumber = combineSerialNumberAndPartNumber(serialNumber, partNumber);

		FruDevice fruDevice = null;
		// vendor or model ?
		if (!Utils.isEmpty(vendor) || !Utils.isEmpty(model)) {
			fruDevice  = new FruDevice(vendor, model, serialNumber);
			poorFruList.addLast(fruDevice);
		}
		return fruDevice;
	}

	/**
	 * Create a new {@link FruDevice} from the given {@link ProductInfo} instance. Then update the very good, good or poor FRU list
	 *
	 * @param veryGoodFruList Very good FRU List
	 * @param goodFruList     Second position, good FRU List
	 * @param poorFruList     The last position, poor FRU list
	 * @param entityId        The {@link EntityId} of the FRU device, {@link EntityId} simply shows the entity id
	 * @param productInfo     The {@link ProductInfo} instance which is the best option when extracting FRU information
	 * @return new {@link FruDevice} instance
	 */
	private static FruDevice createFruDeviceFromProductInfo(final LinkedList<FruDevice> veryGoodFruList, final LinkedList<FruDevice> goodFruList,
			LinkedList<FruDevice> poorFruList, EntityId entityId, ProductInfo productInfo) {

		String vendor = Utils.getValueOrEmpty(productInfo.getManufacturerName());
		String model = Utils.getValueOrEmpty(productInfo.getProductName());
		String serialNumber = Utils.getValueOrEmpty(productInfo.getProductSerialNumber());
		String partNumber = Utils.getValueOrEmpty(productInfo.getProductModelNumber());

		// Combine serialNumber and partNumber
		serialNumber = combineSerialNumberAndPartNumber(serialNumber, partNumber);

		FruDevice fruDevice = new FruDevice(vendor, model, serialNumber);

		// How good and precise is this FRU entry?
		if (!Utils.isEmpty(model) && !Utils.isEmpty(serialNumber)) {
			if (isSystemFru(entityId)) {
				veryGoodFruList.addLast(fruDevice);
			} else if (isFruPanel(entityId)) {
				// Put it in front of the good FRU list
				goodFruList.addFirst(fruDevice);
			} else {
				goodFruList.addLast(fruDevice);
			}
		} else {
			poorFruList.addLast(fruDevice);
		}

		return fruDevice;
	}

	/**
	 * Combine Serial Number and Part Number
	 * @param serialNumber Serial number of the FRU device
	 * @param partNumber   Part Number, product board number or real part number of the FRU device
	 * @return {@link String} value
	 */
	private static String combineSerialNumberAndPartNumber(String serialNumber, final String partNumber) {
		if (Utils.isEmpty(serialNumber) && !Utils.isEmpty(partNumber)) {
			serialNumber = partNumber;
		} else if (!Utils.isEmpty(serialNumber) && !Utils.isEmpty(partNumber)) {
			serialNumber = String.format("%s - %s", serialNumber, partNumber);
		}
		return serialNumber;
	}

	/**
	 *
	 * @param entityId The {@link EntityId} instance defining the type of the device
	 * @return <code>true</code> if the EntityId is a panel
	 */
	private static boolean isFruPanel(final EntityId entityId) {
		return EntityId.FrontPanelBoard.equals(entityId) || EntityId.BackPanelBoard.equals(entityId)
				|| EntityId.ChassisBackPanelBoard.equals(entityId);
	}

	/**
	 *
	 * @param entityId The {@link EntityId} instance defining the type of the device
	 * @return <code>true</code> if the EntityId is the system
	 */
	private static boolean isSystemFru(final EntityId entityId) {
		return EntityId.SystemChassis.equals(entityId) || EntityId.SystemBoard.equals(entityId);
	}

	/**
	 * @param productInfo Fru record as product information
	 * @return <code>true</code> if <code>pruductInfo</code> is not null and the manufacturer name is not null or empty
	 */
	private static boolean hasValidProductInfo(final ProductInfo productInfo) {
		return productInfo != null && !Utils.isBlank(productInfo.getManufacturerName());
	}

	/**
	 * @param <T>
	 * @param fruRecords List of {@link FruRecord} instances containing {@link BoardInfo} and {@link ProductInfo} instances
	 * @param clazz
	 * @return The first matching instance
	 */
	private static <T extends FruRecord> T getInfo(final List<FruRecord> fruRecords, final Class<T> clazz) {
		if (fruRecords == null) {
			return null;
		}

		return fruRecords.stream()
				.filter(clazz::isInstance)
				.map(clazz::cast)
				.findFirst()
				.orElse(null);

	}

	/**
	 * Get the threshold value as String
	 *
	 * @param conversionFunction The conversion function used to convert the threshold value
	 * @param threshold          The double value returned by the IPMI Full record
	 * @return String value
	 */
	private static String getThresholdValue(final DoubleFunction<Double> conversionFunction, double threshold) {
		return threshold != 0.0 ? String.valueOf(Math.round(conversionFunction.apply(threshold))) : Utils.EMPTY;
	}

	/**
	 * @param conversionFunction The conversion function used to convert the threshold value
	 * @param thresholds         The array of the threshold values
	 * @return The first available threshold in <code>thresholds</code>
	 */
	private static String getAvailableThreshold(final DoubleFunction<Double> conversionFunction, double... thresholds) {

		return Arrays.stream(thresholds)
				.filter(threshold -> threshold != 0.0)
				.mapToObj(threshold -> getThresholdValue(conversionFunction, threshold))
				.findFirst()
				.orElse(Utils.EMPTY);
	}

	/**
	 * Convert the given {@link GetChassisStatusResponseData} to a string value
	 *
	 * @param chassisStatus Wrapper for Get Chassis Status response.
	 * @return String value
	 */
	public static String convertResult(final GetChassisStatusResponseData chassisStatus) {
		if (chassisStatus == null) {
			return null;
		}

		return "System power state is " + (chassisStatus.isPowerOn() ? "up" : "down");
	}

}
