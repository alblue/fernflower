package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class LSTORE extends Instruction {

	private static int[] opcodes = new int[] {opc_lstore_0,opc_lstore_1,opc_lstore_2,opc_lstore_3}; 
	
	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int index = getOperand(0);
		if(index>3) {
			if(wide) {
				out.writeByte(opc_wide);
			}
			out.writeByte(opc_lstore);
			if(wide) {
				out.writeShort(index);
			} else {
				out.writeByte(index);
			}
		} else {
			out.writeByte(opcodes[index]);
		}
	}
	
	public int length() {
		int index = getOperand(0);
		if(index>3) {
			return wide?4:2;
		} else {
			return 1;
		}
	}
	
}
