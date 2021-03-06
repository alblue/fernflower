package de.fernflower.code.optinstructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class INVOKEDYNAMIC extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_invokedynamic);
		out.writeShort(getOperand(0));
		out.writeByte(0);
		out.writeByte(0);
	}
	
	public int length() {
		return 5;
	}
}
