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

package de.fernflower.struct.gen.generics;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.struct.StructClass;

public class GenericMain {
	
	private static final String[] typeNames = new String[] {
		"byte", 
		"char",
		"double",
		"float",
		"int",
		"long",
		"short",
		"boolean",
	};

	public static GenericClassDescriptor parseClassSignature(String signature) {
		
		GenericClassDescriptor descriptor = new GenericClassDescriptor();
		
		signature = parseFormalParameters(signature, descriptor.fparameters, descriptor.fbounds);

		String supercl = GenericType.getNextType(signature);
		descriptor.superclass = new GenericType(supercl);
		
		signature = signature.substring(supercl.length());
		while(signature.length() > 0) {
			String superintr = GenericType.getNextType(signature);
			descriptor.superinterfaces.add(new GenericType(superintr));
			signature = signature.substring(superintr.length());
		}
		
		return descriptor;
	}

	public static GenericFieldDescriptor parseFieldSignature(String signature) {
		GenericFieldDescriptor descriptor = new GenericFieldDescriptor();
		descriptor.type = new GenericType(signature);
		return descriptor; 
	}

	public static GenericMethodDescriptor parseMethodSignature(String signature) {
		
		GenericMethodDescriptor descriptor = new GenericMethodDescriptor();		

		signature = parseFormalParameters(signature, descriptor.fparameters, descriptor.fbounds);

		int to = signature.indexOf(")");
		String pars = signature.substring(1, to);
		signature = signature.substring(to+1); 
		
		while(pars.length() > 0) {
			String par = GenericType.getNextType(pars);
			descriptor.params.add(new GenericType(par));
			pars = pars.substring(par.length());
		}
		
		String par = GenericType.getNextType(signature);
		descriptor.ret = new GenericType(par);
		signature = signature.substring(par.length());

		if(signature.length() > 0) {
			String[] excs = signature.split("\\^");
			
			for(int i=1;i<excs.length;i++) {
				descriptor.exceptions.add(new GenericType(excs[i]));
			}
		}
		
		return descriptor;
	}
	
	private static String parseFormalParameters(String signature, List<String> fparameters, List<List<GenericType>> fbounds) {
		
		if(signature.charAt(0) != '<') {
			return signature;
		}
		
		int counter = 1;
		int index = 1;
		
		loop:
		while(index < signature.length()) {
			switch(signature.charAt(index)) {
			case '<':
				counter++;
				break;
			case '>':
				counter--;
				if(counter == 0) {
					break loop;
				}
			}
			
			index++;
		}
		
		String value = signature.substring(1, index);
		signature = signature.substring(index+1); 
		
		while(value.length() > 0) {
			int parto = value.indexOf(":");
			
			String param = value.substring(0, parto);
			value = value.substring(parto+1);
			
			List<GenericType> lstBounds = new ArrayList<GenericType>();
			
			for(;;) {
				if(value.charAt(0) == ':') {
				  // empty superclass, skip
					value = value.substring(1);
				} 
				
				String bound = GenericType.getNextType(value);
				lstBounds.add(new GenericType(bound));
				value = value.substring(bound.length());
				
				
				if(value.length() == 0 || value.charAt(0) != ':') {
					break;
				} else {
					value = value.substring(1);
				}
			}

			fparameters.add(param);
			fbounds.add(lstBounds);
		}
		
		return signature;	
	}

	public static String getGenericCastTypeName(GenericType type) {
		String s = getTypeName(type);
		int dim = type.arraydim;
		while(dim-->0) {
			s+="[]";
		}
		return s;
	}
	
	public static String getTypeName(GenericType type) {
		
		int tp = type.type;
		if(tp <= CodeConstants.TYPE_BOOLEAN) {
			return typeNames[tp];
		} else if(tp == CodeConstants.TYPE_VOID) {
			return "void";
		} else if(tp == CodeConstants.TYPE_GENVAR) {
			return type.value;
		} else if(tp == CodeConstants.TYPE_OBJECT) {
			StringBuilder buffer = new StringBuilder();
			buffer.append(DecompilerContext.getImpcollector().getShortName(buildJavaClassName(type)));
			
			if(!type.getArguments().isEmpty()) {
				buffer.append("<");
				for(int i=0;i<type.getArguments().size();i++) {
					if(i>0) {
						buffer.append(", ");
					}
					int wildcard = type.getWildcards().get(i);
					if(wildcard != GenericType.WILDCARD_NO) {
						buffer.append("?");
						
						switch(wildcard){
						case GenericType.WILDCARD_EXTENDS:
							buffer.append(" extends ");
							break;
						case GenericType.WILDCARD_SUPER:
							buffer.append(" super ");
						}
					}
					
					GenericType genpar = type.getArguments().get(i);
					if(genpar != null) {
						buffer.append(GenericMain.getGenericCastTypeName(genpar));
					}
				}
				buffer.append(">");
			}
			
			return buffer.toString();
		}
		
		throw new RuntimeException("invalid type");
	}
	
	public static String buildJavaClassName(GenericType type) {
		
		String name = "";
		for(GenericType tp : type.getEnclosingClasses()) {
			name += tp.value+"$";
		}
		name+=type.value;
		
		String res = name.replace('/', '.');
		
		if(res.indexOf("$") >=0) {
			StructClass cl = DecompilerContext.getStructcontext().getClass(name);
			if(cl == null || !cl.isOwn()) {
				res = res.replace('$', '.');
			}
		}
		
		return res;
	}
	
}
