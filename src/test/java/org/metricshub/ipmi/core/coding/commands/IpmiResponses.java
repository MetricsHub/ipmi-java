package org.metricshub.ipmi.core.coding.commands;

import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv15Message;

/**
 * Builds IPMI LAN responses as a BMC would send them, for the command coder tests.
 */
public final class IpmiResponses {

	private IpmiResponses() {}

	/**
	 * Build a message wrapping an IPMI LAN response (IPMI 2.0 table 13-5) from the BMC to remote console software.
	 *
	 * @param command the command code the response answers
	 * @param completionCode the completion code byte
	 * @param data the response data bytes, after the completion code
	 * @return the message, with valid checksums
	 */
	public static IpmiMessage response(byte command, int completionCode, int... data) {
		byte[] raw = new byte[8 + data.length];
		raw[0] = (byte) 0x81; // requester address
		raw[1] = (byte) 0x2c; // storage response network function, LUN 0
		raw[2] = (byte) -(0x81 + 0x2c); // checksum 1
		raw[3] = 0x20; // responder address
		raw[4] = 0x04; // sequence number 1, LUN 0
		raw[5] = command;
		raw[6] = (byte) completionCode;
		for (int i = 0; i < data.length; i++) {
			raw[7 + i] = (byte) data[i];
		}
		int checksum = 0;
		for (int i = 3; i < raw.length - 1; i++) {
			checksum += raw[i];
		}
		raw[raw.length - 1] = (byte) -checksum; // checksum 2
		IpmiMessage message = new Ipmiv15Message();
		message.setPayload(new IpmiLanResponse(raw));
		return message;
	}
}
