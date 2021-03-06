package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class IINC extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		if(wide) {
			out.writeByte(opc_wide);
		}
		out.writeByte(opc_iinc);
		if(wide) {
			out.writeShort(getOperand(0));
			out.writeShort(getOperand(1));
		} else {
			out.writeByte(getOperand(0));
			out.writeByte(getOperand(1));
		}
	}	
	
	public int length() {
		return wide?6:3;
	}
	
}
