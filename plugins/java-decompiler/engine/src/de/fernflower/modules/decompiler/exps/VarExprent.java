/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.modules.decompiler.exps;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassWriter;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.modules.decompiler.vars.VarTypeProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class VarExprent extends Exprent {

	public static final int STACK_BASE = 10000;

	public static final String VAR_NAMELESS_ENCLOSURE = "<VAR_NAMELESS_ENCLOSURE>";
	
	private int index;
	
	private VarType vartype;
	
	private boolean definition = false;;
	
	private VarProcessor processor;
	
	private int version = 0;
	
	private boolean classdef = false;
	
	private boolean stack = false;;
	
	{
		this.type = EXPRENT_VAR;
	}

	public VarExprent(int index, VarType vartype, VarProcessor processor) {
		this.index = index;
		this.vartype = vartype;
		this.processor = processor;
	}
	
	public VarType getExprType() {
		return getVartype();
	}
	
	public int getExprentUse() {
		return Exprent.MULTIPLE_USES | Exprent.SIDE_EFFECTS_FREE;
	}
	
	public List<Exprent> getAllExprents() {
		return new ArrayList<Exprent>();
	}
	
	public Exprent copy() {
		VarExprent var = new VarExprent(index, getVartype(), processor);
		var.setDefinition(definition);
		var.setVersion(version);
		var.setClassdef(classdef);
		var.setStack(stack);
		return var;
	}
	
	public String toJava(int indent) {
		
		if(classdef) {
			
			ClassNode child = DecompilerContext.getClassprocessor().getMapRootClasses().get(vartype.value); 
			
			StringWriter strwriter = new StringWriter();
			BufferedWriter bufstrwriter = new BufferedWriter(strwriter);
			
			ClassWriter clwriter = new ClassWriter();
			try {
				clwriter.classToJava(child, bufstrwriter, indent);
				bufstrwriter.flush();
			} catch(IOException ex) {
				throw new RuntimeException(ex);
			}
			
			return strwriter.toString();

		} else {
			String name = null;
			if(processor != null) {
				name = processor.getVarName(new VarVersionPaar(index, version));
			}

			StringBuilder buf = new StringBuilder();
			
			if(definition) {
				if(processor != null && processor.getVarFinal(new VarVersionPaar(index, version)) == VarTypeProcessor.VAR_FINALEXPLICIT) {
					buf.append("final ");
				}
				buf.append(ExprProcessor.getCastTypeName(getVartype())+" ");
			}
			buf.append(name==null?("var"+index+(version==0?"":"_"+version)):name);
			
			return buf.toString();
		}
	}

	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof VarExprent)) return false;

    VarExprent ve = (VarExprent)o;
    return index == ve.getIndex() &&
        version == ve.getVersion() &&
        InterpreterUtil.equalObjects(getVartype(), ve.getVartype()); // FIXME: vartype comparison redundant?
  }
	
	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
	
	public VarType getVartype() {   
		VarType vt = null;
		if(processor != null) {
			vt = processor.getVarType(new VarVersionPaar(index, version));
		}
		
		if(vt == null || (vartype != null && vartype.type != CodeConstants.TYPE_UNKNOWN)) { 
			vt = vartype;
		} 
		
		return vt==null?VarType.VARTYPE_UNKNOWN:vt;
	}

	public void setVartype(VarType vartype) {
		this.vartype = vartype;
	}
	
	public boolean isDefinition() {
		return definition;
	}

	public void setDefinition(boolean definition) {
		this.definition = definition;
	}

	public VarProcessor getProcessor() {
		return processor;
	}

	public void setProcessor(VarProcessor processor) {
		this.processor = processor;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public boolean isClassdef() {
		return classdef;
	}

	public void setClassdef(boolean classdef) {
		this.classdef = classdef;
	}

	public boolean isStack() {
		return stack;
	}

	public void setStack(boolean stack) {
		this.stack = stack;
	}
}
