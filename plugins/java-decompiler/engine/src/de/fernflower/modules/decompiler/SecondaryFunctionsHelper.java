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

package de.fernflower.modules.decompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.IfExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.stats.IfStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.gen.VarType;

public class SecondaryFunctionsHelper {

	private static final int[] funcsnot = new int[] {
		FunctionExprent.FUNCTION_NE,
		FunctionExprent.FUNCTION_EQ,
		FunctionExprent.FUNCTION_GE,
		FunctionExprent.FUNCTION_LT,
		FunctionExprent.FUNCTION_LE,
		FunctionExprent.FUNCTION_GT,
		FunctionExprent.FUNCTION_COR,
		FunctionExprent.FUNCTION_CADD
	};
	
	private static final HashMap<Integer, Integer[]> mapNumComparisons = new HashMap<Integer, Integer[]>();
	
	static {
		mapNumComparisons.put(FunctionExprent.FUNCTION_EQ, new Integer[] {FunctionExprent.FUNCTION_LT, FunctionExprent.FUNCTION_EQ, FunctionExprent.FUNCTION_GT});
		mapNumComparisons.put(FunctionExprent.FUNCTION_NE, new Integer[] {FunctionExprent.FUNCTION_GE, FunctionExprent.FUNCTION_NE, FunctionExprent.FUNCTION_LE});
		mapNumComparisons.put(FunctionExprent.FUNCTION_GT, new Integer[] {FunctionExprent.FUNCTION_GE, FunctionExprent.FUNCTION_GT, null});
		mapNumComparisons.put(FunctionExprent.FUNCTION_GE, new Integer[] {null, FunctionExprent.FUNCTION_GE, FunctionExprent.FUNCTION_GT});
		mapNumComparisons.put(FunctionExprent.FUNCTION_LT, new Integer[] {null, FunctionExprent.FUNCTION_LT, FunctionExprent.FUNCTION_LE});
		mapNumComparisons.put(FunctionExprent.FUNCTION_LE, new Integer[] {FunctionExprent.FUNCTION_LT, FunctionExprent.FUNCTION_LE, null});
	}
	
	
	public static boolean identifySecondaryFunctions(Statement stat) {

		if(stat.getExprents() == null) {
			// if(){;}else{...} -> if(!){...}
			if(stat.type == Statement.TYPE_IF) {
				IfStatement ifelsestat = (IfStatement)stat;
				Statement ifstat = ifelsestat.getIfstat();
				
				if(ifelsestat.iftype == IfStatement.IFTYPE_IFELSE && ifstat.getExprents() != null &&
						ifstat.getExprents().isEmpty() && (ifstat.getAllSuccessorEdges().isEmpty() || !ifstat.getAllSuccessorEdges().get(0).explicit)) {
					
					// move else to the if position
					ifelsestat.getStats().removeWithKey(ifstat.id);

					ifelsestat.iftype = IfStatement.IFTYPE_IF;
					ifelsestat.setIfstat(ifelsestat.getElsestat());
					ifelsestat.setElsestat(null);
					
					if(ifelsestat.getAllSuccessorEdges().isEmpty() && !ifstat.getAllSuccessorEdges().isEmpty()) {
						StatEdge endedge = ifstat.getAllSuccessorEdges().get(0);
						
						ifstat.removeSuccessor(endedge);
						endedge.setSource(ifelsestat);
						if(endedge.closure != null) {
							ifelsestat.getParent().addLabeledEdge(endedge);
						}
						ifelsestat.addSuccessor(endedge);
					}
					
					ifelsestat.getFirst().removeSuccessor(ifelsestat.getIfEdge());
					
					ifelsestat.setIfEdge(ifelsestat.getElseEdge());
					ifelsestat.setElseEdge(null);
					
					// negate head expression
					ifelsestat.setNegated(!ifelsestat.isNegated());
					ifelsestat.getHeadexprentList().set(0, ((IfExprent)ifelsestat.getHeadexprent().copy()).negateIf());
					
					return true;
				}
			}
		}
		
		
		boolean replaced = true;
		while(replaced) {
			replaced = false;
			
			List<Object> lstObjects = new ArrayList<Object>(stat.getExprents()==null?stat.getSequentialObjects():stat.getExprents());

			for(int i=0;i<lstObjects.size();i++) {
				Object obj = lstObjects.get(i); 

				if(obj instanceof Statement) {
					if(identifySecondaryFunctions((Statement)obj)) {
						replaced = true;
						break;
					}
				} else if(obj instanceof Exprent) {
					Exprent retexpr = identifySecondaryFunctions((Exprent)obj, true);
					if(retexpr != null) {
						if(stat.getExprents()==null) {
							// only head expressions can be replaced!
							stat.replaceExprent((Exprent)obj, retexpr);
						} else {
							stat.getExprents().set(i, retexpr);
						}
						replaced = true;
						break;
					}
				}
			}
		}

		return false;
	}
	
	
	private static Exprent identifySecondaryFunctions(Exprent exprent, boolean statement_level) {
		
		if(exprent.type == Exprent.EXPRENT_FUNCTION) {
			FunctionExprent fexpr = (FunctionExprent)exprent;

			switch(fexpr.getFunctype()) {
			case FunctionExprent.FUNCTION_BOOLNOT:

				Exprent retparam = propagateBoolNot(fexpr);
				
				if(retparam != null) {
					return retparam;
				}
				
				break;
			case FunctionExprent.FUNCTION_EQ:
			case FunctionExprent.FUNCTION_NE:
			case FunctionExprent.FUNCTION_GT:
			case FunctionExprent.FUNCTION_GE:
			case FunctionExprent.FUNCTION_LT:
			case FunctionExprent.FUNCTION_LE:
				Exprent expr1 = fexpr.getLstOperands().get(0);
				Exprent expr2 = fexpr.getLstOperands().get(1);
				
				if(expr1.type == Exprent.EXPRENT_CONST) {
					expr2 = expr1;
					expr1 = fexpr.getLstOperands().get(1);
				}
				
				if(expr1.type == Exprent.EXPRENT_FUNCTION && expr2.type == Exprent.EXPRENT_CONST) {
					FunctionExprent funcexpr = (FunctionExprent)expr1;
					ConstExprent cexpr = (ConstExprent)expr2;
					
					int functype = funcexpr.getFunctype();
					if(functype == FunctionExprent.FUNCTION_LCMP || functype == FunctionExprent.FUNCTION_FCMPG ||
							functype == FunctionExprent.FUNCTION_FCMPL || functype == FunctionExprent.FUNCTION_DCMPG ||
							functype == FunctionExprent.FUNCTION_DCMPL) {
						
						int desttype = -1;

						Integer[] destcons = mapNumComparisons.get(fexpr.getFunctype());
						if(destcons != null) {
							int index = cexpr.getIntValue()+1;
							if(index >= 0 && index <= 2) {
								Integer destcon = destcons[index];
								if(destcon != null) {
									desttype = destcon.intValue();
								}
							}
						}
						
						if(desttype >= 0) {
							return new FunctionExprent(desttype, funcexpr.getLstOperands());
						}
					}
				}
			}
		}
		
		
		boolean replaced = true;
		while(replaced) {
			replaced = false;
			
			for(Exprent expr: exprent.getAllExprents()) {
				Exprent retexpr = identifySecondaryFunctions(expr, false);
				if(retexpr != null) {
					exprent.replaceExprent(expr, retexpr);
					replaced = true;
					break;
				}
			}
		}
		
		switch(exprent.type) {
		case Exprent.EXPRENT_FUNCTION:
			FunctionExprent fexpr = (FunctionExprent)exprent;
			List<Exprent> lstOperands = fexpr.getLstOperands();
			
			switch(fexpr.getFunctype()) {
			case FunctionExprent.FUNCTION_XOR:
				for(int i=0;i<2;i++) {
					Exprent operand = lstOperands.get(i);
					VarType operandtype = operand.getExprType();
					
					if(operand.type == Exprent.EXPRENT_CONST && 
							operandtype.type != CodeConstants.TYPE_BOOLEAN) {
						ConstExprent cexpr = (ConstExprent)operand;
						long val;
						if(operandtype.type == CodeConstants.TYPE_LONG) {
							val = ((Long)cexpr.getValue()).longValue();
						} else {
							val = ((Integer)cexpr.getValue()).intValue();
						}
						
						if(val == -1) {
							List<Exprent> lstBitNotOperand = new ArrayList<Exprent>();
							lstBitNotOperand.add(lstOperands.get(1-i));
							return new FunctionExprent(FunctionExprent.FUNCTION_BITNOT, lstBitNotOperand); 
						}
					}
				}
				break;
			case FunctionExprent.FUNCTION_EQ:
			case FunctionExprent.FUNCTION_NE:
				if(lstOperands.get(0).getExprType().type == CodeConstants.TYPE_BOOLEAN && 
						lstOperands.get(1).getExprType().type == CodeConstants.TYPE_BOOLEAN) {
					for(int i=0;i<2;i++) {
						if(lstOperands.get(i).type == Exprent.EXPRENT_CONST) {
							ConstExprent cexpr = (ConstExprent)lstOperands.get(i);
							int val = ((Integer)cexpr.getValue()).intValue();
							
							if((fexpr.getFunctype() == FunctionExprent.FUNCTION_EQ && val == 1) ||
									(fexpr.getFunctype() == FunctionExprent.FUNCTION_NE && val == 0)) {
								return lstOperands.get(1-i);
							} else {
								List<Exprent> lstNotOperand = new ArrayList<Exprent>();
								lstNotOperand.add(lstOperands.get(1-i));
								return new FunctionExprent(FunctionExprent.FUNCTION_BOOLNOT, lstNotOperand); 
							}
						}
					}
				}
				break;
			case FunctionExprent.FUNCTION_BOOLNOT:
				if(lstOperands.get(0).type == Exprent.EXPRENT_CONST) {
					int val = ((ConstExprent)lstOperands.get(0)).getIntValue();
					if(val == 0) {
						return new ConstExprent(VarType.VARTYPE_BOOLEAN, new Integer(1));
					} else {
						return new ConstExprent(VarType.VARTYPE_BOOLEAN, new Integer(0));
					}
				}
				break;
			case FunctionExprent.FUNCTION_IIF:
				Exprent expr1 = lstOperands.get(1);
				Exprent expr2 = lstOperands.get(2);
				
				if(expr1.type == Exprent.EXPRENT_CONST && expr2.type == Exprent.EXPRENT_CONST) {
					ConstExprent cexpr1 = (ConstExprent)expr1;
					ConstExprent cexpr2 = (ConstExprent)expr2;
					
					if(cexpr1.getExprType().type == CodeConstants.TYPE_BOOLEAN && 
							cexpr2.getExprType().type == CodeConstants.TYPE_BOOLEAN) {
						
						if(cexpr1.getIntValue() == 0 && cexpr2.getIntValue() != 0) {
							return new FunctionExprent(FunctionExprent.FUNCTION_BOOLNOT, Arrays.asList(new Exprent[] {lstOperands.get(0)}));							
						} else if(cexpr1.getIntValue() != 0 && cexpr2.getIntValue() == 0) {
							return lstOperands.get(0);
						}
					}
				}
				break;
			case FunctionExprent.FUNCTION_LCMP:
			case FunctionExprent.FUNCTION_FCMPL:
			case FunctionExprent.FUNCTION_FCMPG:
			case FunctionExprent.FUNCTION_DCMPL:
			case FunctionExprent.FUNCTION_DCMPG:
				int var = DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER);
				VarType type = lstOperands.get(0).getExprType();
				VarProcessor processor = (VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR);
				
				FunctionExprent iff = new FunctionExprent(FunctionExprent.FUNCTION_IIF, Arrays.asList(new Exprent[] {
						new FunctionExprent(FunctionExprent.FUNCTION_LT, Arrays.asList(new Exprent[] {new VarExprent(var, type, processor), 
																						ConstExprent.getZeroConstant(type.type)})),
						new ConstExprent(VarType.VARTYPE_INT, new Integer(-1)),
						new ConstExprent(VarType.VARTYPE_INT, new Integer(1))}));
				
				FunctionExprent head = new FunctionExprent(FunctionExprent.FUNCTION_EQ, Arrays.asList(new Exprent[] {
					new AssignmentExprent(new VarExprent(var, type, processor), new FunctionExprent(FunctionExprent.FUNCTION_SUB, 
							Arrays.asList(new Exprent[] {lstOperands.get(0), lstOperands.get(1)}))),	
							ConstExprent.getZeroConstant(type.type)}));
				
				processor.setVarType(new VarVersionPaar(var, 0), type);
				
				return new FunctionExprent(FunctionExprent.FUNCTION_IIF, Arrays.asList(new Exprent[] {
					head, new ConstExprent(VarType.VARTYPE_INT, new Integer(0)), iff}));
			}
			break;
		case Exprent.EXPRENT_ASSIGNMENT: // check for conditional assignment
			AssignmentExprent asexpr = (AssignmentExprent)exprent;
			Exprent right = asexpr.getRight();
			Exprent left = asexpr.getLeft();
			
			if(right.type == Exprent.EXPRENT_FUNCTION) {
				FunctionExprent func = (FunctionExprent)right;
				
				VarType midlayer = null;
				if(func.getFunctype() >= FunctionExprent.FUNCTION_I2L && 
						func.getFunctype() <= FunctionExprent.FUNCTION_I2S) {
					right = func.getLstOperands().get(0);
					midlayer = func.getSimpleCastType();
					if(right.type == Exprent.EXPRENT_FUNCTION) {
						func = (FunctionExprent)right;
					} else {
						return null;
					}
				}
				
				List<Exprent> lstFuncOperands = func.getLstOperands();
				
				Exprent cond = null;
				
				switch(func.getFunctype()) {
				case FunctionExprent.FUNCTION_ADD:
				case FunctionExprent.FUNCTION_AND:
				case FunctionExprent.FUNCTION_OR:
				case FunctionExprent.FUNCTION_XOR:
					if(left.equals(lstFuncOperands.get(1))) {
						cond = lstFuncOperands.get(0);
						break;
					}
				case FunctionExprent.FUNCTION_SUB:
				case FunctionExprent.FUNCTION_MUL:
				case FunctionExprent.FUNCTION_DIV:
				case FunctionExprent.FUNCTION_REM:
				case FunctionExprent.FUNCTION_SHL:
				case FunctionExprent.FUNCTION_SHR:
				case FunctionExprent.FUNCTION_USHR:
					if(left.equals(lstFuncOperands.get(0))) {
						cond = lstFuncOperands.get(1);
					}
				}
				
				if(cond!=null && (midlayer == null || midlayer.equals(cond.getExprType()))) {
					asexpr.setRight(cond);
					asexpr.setCondtype(func.getFunctype()); 
				}
			}
			break;
		case Exprent.EXPRENT_INVOCATION:
			if(!statement_level) { // simplify if exprent is a real expression. The opposite case is pretty absurd, can still happen however (and happened at least once).  
				Exprent retexpr = ConcatenationHelper.contractStringConcat(exprent);
				if(!exprent.equals(retexpr)) {
					return retexpr;
				}
			}
		}
		
		return null;
		
	}
	
	public static Exprent propagateBoolNot(Exprent exprent) {

		if(exprent.type == Exprent.EXPRENT_FUNCTION) {
			FunctionExprent fexpr = (FunctionExprent)exprent;

			if(fexpr.getFunctype() == FunctionExprent.FUNCTION_BOOLNOT) {

				Exprent param  = fexpr.getLstOperands().get(0);

				if(param.type == Exprent.EXPRENT_FUNCTION) {
					FunctionExprent fparam = (FunctionExprent)param;

					int ftype = fparam.getFunctype();
					switch(ftype) {
					case FunctionExprent.FUNCTION_BOOLNOT:
						Exprent newexpr = fparam.getLstOperands().get(0);
						Exprent retexpr = propagateBoolNot(newexpr);  
						return retexpr == null?newexpr:retexpr;
					case FunctionExprent.FUNCTION_CADD:
					case FunctionExprent.FUNCTION_COR:
						List<Exprent> operands = fparam.getLstOperands();
						for(int i=0;i<operands.size();i++) {
							Exprent newparam = new FunctionExprent(FunctionExprent.FUNCTION_BOOLNOT, 
									Arrays.asList(new Exprent[]{operands.get(i)}));
							
							Exprent retparam = propagateBoolNot(newparam);  
							operands.set(i, retparam == null?newparam:retparam);
						}
					case FunctionExprent.FUNCTION_EQ:
					case FunctionExprent.FUNCTION_NE:
					case FunctionExprent.FUNCTION_LT:
					case FunctionExprent.FUNCTION_GE:
					case FunctionExprent.FUNCTION_GT:
					case FunctionExprent.FUNCTION_LE:
						fparam.setFunctype(funcsnot[ftype-FunctionExprent.FUNCTION_EQ]);
						return fparam;
					}
				}
			}
		}

		return null;
	}
	
}
