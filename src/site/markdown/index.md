# IPMI Java Client

The IPMI Java Client is a library that communicates with the IPMI host, fetches Field Replaceable Units (FRUs) and Sensors information then reports these information as a text output.

## How to run the IPMI Client inside Java

Add IPMI in the list of dependencies in your [Maven **pom.xml**](https://maven.apache.org/pom.html):

```xml
<dependencies>
	<dependency>
		<groupId>${project.groupId}</groupId>
		<artifactId>${project.artifactId}</artifactId>
		<version>${project.version}</version>
	</dependency>
</dependencies>
```

Invoke the IPMI Client:

```java

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;

public class IpmiMain {
	public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {

		final String hostname = "my-host";
		final String username = "my-username";
		final char[] password = new char[] { 'p', 'a', 's', 's' };
		final boolean noAuth = false;
		final byte[] bmcKey = null;
		final long timeout = 120;
		// Set pingPeriod to 0 to turn off keep-alive messages sent to the remote host.
		final long pingPeriod = 30000;

		// Instantiates a new IPMI client configuration using the credentials above
		final IpmiClientConfiguration ipmiClientConfiguration = new IpmiClientConfiguration(
			hostname,
			username,
			password,
			bmcKey,
			noAuth,
			timeout,
			pingPeriod
		);

		// Get the Chassis' status
		final String chassisStatusResult = IpmiClient.getChassisStatusAsStringResult(ipmiClientConfiguration);

		System.out.println("Chassis status:");
		System.out.println(chassisStatusResult);

		// Get FRUs and Sensors
		final String sensorsResult = IpmiClient.getFrusAndSensorsAsStringResult(ipmiClientConfiguration);

		System.out.println("Sensors:");
		System.out.println(sensorsResult);
	}
}

```

## Upgrading from 1.2.02

The `IpmiClient` API is unchanged. Classes that **extend** the library's protocol classes must replace direct access to formerly `protected` fields, which are now `private`, with the new `protected` accessors:

| Class | Former field | Accessor |
| --- | --- | --- |
| `AbstractIpmiRunner` | `ipmiConfiguration` | `getIpmiConfiguration()` |
| `AbstractIpmiRunner` | `connector` | `getConnector()` |
| `AbstractIpmiRunner` | `handle` | `getHandle()` |
| `AbstractIpmiRunner` | `nextRecId` | `getNextRecId()`, `setNextRecId(int)` |
| `MessageHandler` | `messageQueue` | `getMessageQueue()` |
| `MessageHandler` | `connection` | `getConnection()` |
| `MessageHandler` | `lastReceivedSequenceNumber` | `getLastReceivedSequenceNumber()`, `setLastReceivedSequenceNumber(int)` |
| `IpmiLanMessage` | `networkFunction` | `getNetworkFunctionCode()`, `setNetworkFunctionCode(byte)` |
| `ConfidentialityAlgorithm` | `sik` | `getSik()` |
| `IntegrityAlgorithm` | `sik` | `getSik()`, `setSik(byte[])` |

`IpmiClient`, `IpmiResultConverter`, `Utils`, `DeviceDescription`, `ReadingTypeDescription` and `MessageComposer` are now `final` (they only had private constructors, so they could not be subclassed anyway).

### Sensor records

`FullSensorRecord`, `CompactSensorRecord` and `EventOnlyRecord` now extend the new `AbstractSensorRecord` (itself a `SensorRecord`), which holds the fields the three record types share: sensor owner and number, entity, sensor type, event/reading type, direction, name (ID string), capabilities, units and record sharing. Their getters and setters keep the same signatures, so existing code compiles unchanged, and code that handles several record types can use `AbstractSensorRecord` instead of testing each type:

```java
if (record instanceof AbstractSensorRecord) {
	AbstractSensorRecord sensor = (AbstractSensorRecord) record;
	System.out.println(sensor.getName() + ": " + sensor.getSensorType());
}
```

A record type now also inherits the getters of fields it does not define, which return defaults:

| Record | Field | Value |
| --- | --- | --- |
| `EventOnlyRecord` (no reading) | `getRateUnit()`, `getModifierUnitUsage()`, `getSensorBaseUnit()`, `getSensorModifierUnit()` | `null` |
| `EventOnlyRecord` (no reading) | `isHysteresisReadable()`, `isThresholdsReadable()` | `false` |
| `FullSensorRecord` (a single sensor) | `getShareCount()`, `getIdInstanceModifierOffset()` | `0` |
| `FullSensorRecord` (a single sensor) | `getIdInstanceModifierType()` | `null` |
| `FullSensorRecord` (a single sensor) | `isEntityInstanceIncrements()` | `false` |

### Command responses

Commands that extend `IpmiCommandCoder` can call the new `protected` method `validateResponse(IpmiMessage)`, which checks that a message is a successful response to the command and returns its data: it throws `IllegalArgumentException` for a response to another command or a payload that is not an IPMI LAN response, and `IPMIException` for a completion code other than `Ok`. The message of the `IllegalArgumentException` now names the command class (three commands used to name the wrong command).
