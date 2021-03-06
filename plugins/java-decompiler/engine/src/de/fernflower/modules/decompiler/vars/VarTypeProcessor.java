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

package de.fernflower.modules.decompiler.vars;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.stats.CatchAllStatement;
import de.fernflower.modules.decompiler.stats.CatchStatement;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.VarType;

public class VarTypeProcessor {
	
	public static final int VAR_NONFINAL = 1;
	public static final int VAR_FINALEXPLICIT = 2;
	public static final int VAR_FINAL = 3;

	private HashMap<VarVersionPaar, VarType> mapExprentMinTypes = new HashMap<VarVersionPaar, VarType>();

	private HashMap<VarVersionPaar, VarType> mapExprentMaxTypes = new HashMap<VarVersionPaar, VarType>();

	private HashMap<VarVersionPaar, Integer> mapFinalVars = new HashMap<VarVersionPaar, Integer>();

	private void setInitVars(RootStatement root) {

		StructMethod mt = (StructMethod)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD);
		
		// method descriptor
		boolean thisvar = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0;
		
		MethodDescriptor md = (MethodDescriptor)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_DESCRIPTOR);
		
		if(thisvar) {
			VarType cltype = new VarType(CodeConstants.TYPE_OBJECT, 0, 
					((StructClass)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS)).qualifiedName);
			mapExprentMinTypes.put(new VarVersionPaar(0,1), cltype);
			mapExprentMaxTypes.put(new VarVersionPaar(0,1), cltype);
		}

		int varindex = 0;
		for(int i=0;i<md.params.length;i++) {
			mapExprentMinTypes.put(new VarVersionPaar(varindex+(thisvar?1:0), 1), md.params[i]);
			mapExprentMaxTypes.put(new VarVersionPaar(varindex+(thisvar?1:0), 1), md.params[i]);
			varindex+=md.params[i].stack_size;
		}
		
		// catch variables
		LinkedList<Statement> stack = new LinkedList<Statement>();
		stack.add(root);
		
		while(!stack.isEmpty()) {
			Statement stat = stack.removeFirst();
			
			List<VarExprent> lstVars = null; 
			if(stat.type == Statement.TYPE_CATCHALL) {
				lstVars = ((CatchAllStatement)stat).getVars();
			} else if(stat.type == Statement.TYPE_TRYCATCH) {
				lstVars = ((CatchStatement)stat).getVars();
			}
			
			if(lstVars != null) {
				for(VarExprent var: lstVars) {
					mapExprentMinTypes.put(new VarVersionPaar(var.getIndex(), 1), var.getVartype());
					mapExprentMaxTypes.put(new VarVersionPaar(var.getIndex(), 1), var.getVartype());
				}
			}
			
			stack.addAll(stat.getStats());
		}
	}
	
	public void calculateVarTypes(RootStatement root, DirectGraph dgraph) {
		
		setInitVars(root);
		
		resetExprentTypes(dgraph);
		
		while(!processVarTypes(dgraph));
	}
	
	private void resetExprentTypes(DirectGraph dgraph) {

		dgraph.iterateExprents(new DirectGraph.ExprentIterator() {
			public int processExprent(Exprent exprent) {
				List<Exprent> lst = exprent.getAllExprents(true);
				lst.add(exprent);
				
				for(Exprent expr: lst) {
					if(expr.type == Exprent.EXPRENT_VAR) {
						((VarExprent)expr).setVartype(VarType.VARTYPE_UNKNOWN);
					} else if(expr.type == Exprent.EXPRENT_CONST) {
						ConstExprent cexpr = (ConstExprent)expr;
						if(cexpr.getConsttype().type_family == CodeConstants.TYPE_FAMILY_INTEGER) {
							cexpr.setConsttype(new ConstExprent(cexpr.getIntValue(), cexpr.isBoolPermitted()).getConsttype());
						}
					}
				}
				return 0;
			}
		});
	}
	
	private boolean processVarTypes(DirectGraph dgraph) {

		return dgraph.iterateExprents(new DirectGraph.ExprentIterator() {
			public int processExprent(Exprent exprent) {
				return checkTypeExprent(exprent)?0:1;
			}
		});
	}

	
	private boolean checkTypeExprent(Exprent exprent) {

		for(Exprent expr: exprent.getAllExprents()) {
			if(!checkTypeExprent(expr)) {
				return false; 
			}
		}

		if(exprent.type == Exprent.EXPRENT_CONST) {
			ConstExprent cexpr = (ConstExprent)exprent;
			if(cexpr.getConsttype().type_family <= CodeConstants.TYPE_FAMILY_INTEGER) { // boolean or integer
				VarVersionPaar cpaar = new VarVersionPaar(cexpr.id, -1);
				if(!mapExprentMinTypes.containsKey(cpaar)) {
					mapExprentMinTypes.put(cpaar, cexpr.getConsttype());
				}
			}
		}
		
		CheckTypesResult result = exprent.checkExprTypeBounds();
		
		for(CheckTypesResult.ExprentTypePair entry: result.getLstMaxTypeExprents()) {
			if(entry.type.type_family != CodeConstants.TYPE_FAMILY_OBJECT) {
				changeExprentType(entry.exprent, entry.type, 1);
			}
		}
		
		boolean res = true;
		for(CheckTypesResult.ExprentTypePair entry: result.getLstMinTypeExprents()) {
			res &= changeExprentType(entry.exprent, entry.type, 0);
		}
		
		return res;
	}
	
	
	private boolean changeExprentType(Exprent exprent, VarType newtype, int minmax) {

		boolean res = true;
		
		switch(exprent.type) {
		case Exprent.EXPRENT_CONST:
			ConstExprent cexpr = (ConstExprent)exprent;
			VarType consttype = cexpr.getConsttype();
			
			if(newtype.type_family > CodeConstants.TYPE_FAMILY_INTEGER || consttype.type_family > CodeConstants.TYPE_FAMILY_INTEGER) {
				return true;
			} else if(newtype.type_family == CodeConstants.TYPE_FAMILY_INTEGER) {
				VarType mininteger = new ConstExprent((Integer)((ConstExprent)exprent).getValue(), false).getConsttype();
				if(mininteger.isStrictSuperset(newtype)) {
					newtype = mininteger;
				}
			}
		case Exprent.EXPRENT_VAR:
			VarVersionPaar varpaar = null;
			if(exprent.type == Exprent.EXPRENT_CONST) {
				varpaar = new VarVersionPaar(((ConstExprent)exprent).id, -1);
			} else if(exprent.type == Exprent.EXPRENT_VAR) {
				varpaar = new VarVersionPaar((VarExprent)exprent);
			}
			
			if(minmax == 0) { // min
				VarType currentMinType = mapExprentMinTypes.get(varpaar);
				VarType newMinType;
				if(currentMinType==null || newtype.type_family > currentMinType.type_family) {
					newMinType = newtype;
				} else if(newtype.type_family < currentMinType.type_family) {
					return true;
				} else {
					newMinType = VarType.getCommonSupertype(currentMinType, newtype);
				}
				
				mapExprentMinTypes.put(varpaar, newMinType);
				if(exprent.type == Exprent.EXPRENT_CONST) {
					((ConstExprent)exprent).setConsttype(newMinType);
				}
				
				if(currentMinType != null && (newMinType.type_family > currentMinType.type_family ||
													newMinType.isStrictSuperset(currentMinType))) {
					return false;
				}
			} else {  // max
				VarType currentMaxType = mapExprentMaxTypes.get(varpaar);
				VarType newMaxType;
				if(currentMaxType==null || newtype.type_family < currentMaxType.type_family) {
					newMaxType = newtype;
				} else if(newtype.type_family > currentMaxType.type_family) {
					return true;
				} else {
					newMaxType = VarType.getCommonMinType(currentMaxType, newtype);
				}
				
				mapExprentMaxTypes.put(varpaar, newMaxType);
			}
			break;
		case Exprent.EXPRENT_ASSIGNMENT:
			return changeExprentType(((AssignmentExprent)exprent).getRight(), newtype, minmax);
		case Exprent.EXPRENT_FUNCTION:
			FunctionExprent func = (FunctionExprent)exprent;
			switch(func.getFunctype()){
			case FunctionExprent.FUNCTION_IIF:   // FIXME:
				res &= changeExprentType(func.getLstOperands().get(1), newtype, minmax);
				res &= changeExprentType(func.getLstOperands().get(2), newtype, minmax);
				break;
			case FunctionExprent.FUNCTION_AND:
			case FunctionExprent.FUNCTION_OR:
			case FunctionExprent.FUNCTION_XOR:
				res &= changeExprentType(func.getLstOperands().get(0), newtype, minmax);
				res &= changeExprentType(func.getLstOperands().get(1), newtype, minmax);
			}
		}
		
		return res;
	}
	
	public HashMap<VarVersionPaar, VarType> getMapExprentMaxTypes() {
		return mapExprentMaxTypes;
	}

	public HashMap<VarVersionPaar, VarType> getMapExprentMinTypes() {
		return mapExprentMinTypes;
	}

	public HashMap<VarVersionPaar, Integer> getMapFinalVars() {
		return mapFinalVars;
	}

	public void setVarType(VarVersionPaar varpaar, VarType type) {
		mapExprentMinTypes.put(varpaar, type);
	}
	
	public VarType getVarType(VarVersionPaar varpaar) {
		return mapExprentMinTypes.get(varpaar);
	}

}
