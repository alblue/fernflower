package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.JumpInstruction;

public class GOTO_W extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_goto_w);
		out.writeInt(getOperand(0));
	}
	
	public int length() {
		return 5;
	}
	
}
