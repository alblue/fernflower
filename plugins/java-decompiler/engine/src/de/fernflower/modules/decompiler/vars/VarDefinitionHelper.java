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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.VarNamesCollector;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.stats.CatchAllStatement;
import de.fernflower.modules.decompiler.stats.CatchStatement;
import de.fernflower.modules.decompiler.stats.DoStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.MethodDescriptor;

public class VarDefinitionHelper {

	private HashMap<Integer, Statement> mapVarDefStatements;

	// statement.id, defined vars
	private HashMap<Integer, HashSet<Integer>> mapStatementVars;
	
	private HashSet<Integer> implDefVars; 
	
	private VarProcessor varproc;
	
	public VarDefinitionHelper(Statement root, StructMethod mt, VarProcessor varproc) {
		
		mapVarDefStatements = new HashMap<Integer, Statement>();
		mapStatementVars = new HashMap<Integer, HashSet<Integer>>();
		implDefVars = new HashSet<Integer>();
		
		this.varproc = varproc;
		
		VarNamesCollector vc = DecompilerContext.getVarncollector();
		
		boolean thisvar = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0;
		
		MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
		
		int paramcount = 0;
		if(thisvar) {
			paramcount = 1;
		}
		paramcount += md.params.length;

		
		// method parameters are implicitly defined
		int varindex = 0;
		for(int i=0;i<paramcount;i++) {
			implDefVars.add(varindex);
			varproc.setVarName(new VarVersionPaar(varindex, 0), vc.getFreeName(varindex));
			
			if(thisvar) {
				if(i==0) {
					varindex++;
				} else {
					varindex+=md.params[i-1].stack_size;
				}
			} else {
				varindex+=md.params[i].stack_size;
			}
		}
		
		if(thisvar) {
			StructClass current_class = (StructClass)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASS);
			
			varproc.getThisvars().put(new VarVersionPaar(0, 0), current_class.qualifiedName);
			varproc.setVarName(new VarVersionPaar(0, 0), "this");
			vc.addName("this");
		}
		
		// catch variables are implicitly defined
		LinkedList<Statement> stack = new LinkedList<Statement>();
		stack.add(root);
		
		while(!stack.isEmpty()) {
			Statement st = stack.removeFirst();

			List<VarExprent> lstVars = null; 
			if(st.type == Statement.TYPE_CATCHALL) {
				lstVars = ((CatchAllStatement)st).getVars();
			} else if(st.type == Statement.TYPE_TRYCATCH) {
				lstVars = ((CatchStatement)st).getVars();
			}
			
			if(lstVars != null) {
				for(VarExprent var: lstVars) {
					implDefVars.add(var.getIndex());
					varproc.setVarName(new VarVersionPaar(var), vc.getFreeName(var.getIndex()));
					var.setDefinition(true);
				}
			}
				
			stack.addAll(st.getStats());
		}
		
		initStatement(root);
	}
	
	
	public void setVarDefinitions() {

		VarNamesCollector vc = DecompilerContext.getVarncollector();
		
		Iterator<Entry<Integer, Statement>> it = mapVarDefStatements.entrySet().iterator();
		while(it.hasNext()) {
			Entry<Integer, Statement> en = it.next();
			
			Statement stat = en.getValue();
			Integer index = en.getKey();
			
			if(implDefVars.contains(index)) {
				// already implicitly defined
				continue;
			}
			
			varproc.setVarName(new VarVersionPaar(index.intValue(), 0), vc.getFreeName(index));
			
			// special case for
			if(stat.type == Statement.TYPE_DO) {
				DoStatement dstat = (DoStatement)stat;
				if(dstat.getLooptype() == DoStatement.LOOP_FOR) {
					
					if(dstat.getInitExprent() != null && setDefinition(dstat.getInitExprent(), index)) {
						continue;
					} else {
						List<Exprent> lstSpecial = Arrays.asList(new Exprent[]{dstat.getConditionExprent(), dstat.getIncExprent()});
						for(VarExprent var: getAllVars(lstSpecial)) {
							if(var.getIndex() == index.intValue()) {
								stat = stat.getParent();
								break;
							}
						}
					}
				}
			}
			
			
			Statement first = findFirstBlock(stat, index);
			
			List<Exprent> lst;
			if(first == null) {
				lst = stat.getVarDefinitions();
			} else if(first.getExprents() == null) {
				lst = first.getVarDefinitions();
			} else {
				lst = first.getExprents();
			}

			
			boolean defset = false;
			
			// search for the first assignement to var [index]
			int addindex = 0;
			for(Exprent expr: lst) {
				if(setDefinition(expr, index)) {
					defset = true;
					break;
				} else {
					boolean foundvar = false;
					for(Exprent exp: expr.getAllExprents(true)) {
						if(exp.type == Exprent.EXPRENT_VAR && ((VarExprent)exp).getIndex() == index) {
							foundvar = true;
							break;
						}
					}
					if(foundvar) {
						break;
					}
				}
				addindex++;
			}
			
			if(!defset) {
				VarExprent var = new VarExprent(index.intValue(), varproc.getVarType(new VarVersionPaar(index.intValue(), 0)), varproc);
				var.setDefinition(true);
				
				lst.add(addindex, var);
			}
			
		}
		
	}
	
	
	// *****************************************************************************
	// private methods
	// *****************************************************************************
	
	private Statement findFirstBlock(Statement stat, Integer varindex) {

		LinkedList<Statement> stack = new LinkedList<Statement>();
		stack.add(stat);
		
		while(!stack.isEmpty()) {
			Statement st = stack.remove(0);
			
			if(stack.isEmpty() || mapStatementVars.get(st.id).contains(varindex)) {
				
				if(st.isLabeled() && !stack.isEmpty()) {
					return st;
				}
					
				if(st.getExprents() != null) {
					return st;
				} else {
					stack.clear();
					
					switch(st.type) {
					case Statement.TYPE_SEQUENCE:
						stack.addAll(0, st.getStats());
						break;
					case Statement.TYPE_IF:
					case Statement.TYPE_ROOT:
					case Statement.TYPE_SWITCH:
					case Statement.TYPE_SYNCRONIZED:
						stack.add(st.getFirst());
						break;
					default:
						return st;
					}
				}
			}
		}
		
		return null;
	}
	
	private Set<Integer> initStatement(Statement stat) {
		
		HashMap<Integer, Integer> mapCount = new HashMap<Integer, Integer>(); 

		List<VarExprent> condlst;
		
		if(stat.getExprents() == null) {
		
			// recurse on children statements
			List<Integer> childVars = new ArrayList<Integer>();
			List<Exprent> currVars = new ArrayList<Exprent>();
			
			for(Object obj: stat.getSequentialObjects()) {
				if(obj instanceof Statement) {
					Statement st = (Statement)obj;
					childVars.addAll(initStatement(st));
					
					if(st.type == DoStatement.TYPE_DO) {
						DoStatement dost = (DoStatement)st;
						if(dost.getLooptype() != DoStatement.LOOP_FOR &&
								dost.getLooptype() != DoStatement.LOOP_DO) {
							currVars.add(dost.getConditionExprent());
						}
					} else if(st.type == DoStatement.TYPE_CATCHALL) {
						CatchAllStatement fin = (CatchAllStatement)st;
						if(fin.isFinally() && fin.getMonitor() != null) {
							currVars.add(fin.getMonitor());
						}
					}
				} else if(obj instanceof Exprent) {
					currVars.add((Exprent)obj);
				}
			}
			
			// children statements
			for(Integer index: childVars) {
				Integer count = mapCount.get(index);
				if(count == null) {
					count = new Integer(0);
				} 
				mapCount.put(index, new Integer(count.intValue()+1));
			}
			
			condlst = getAllVars(currVars);
		} else {
			condlst = getAllVars(stat.getExprents());
		}
		
		// this statement
		for(VarExprent var: condlst) {
			mapCount.put(new Integer(var.getIndex()), new Integer(2));
		}
		

		HashSet<Integer> set = new HashSet<Integer>(mapCount.keySet());
		
		// put all variables defined in this statement into the set
		Iterator<Entry<Integer, Integer>> itMult = mapCount.entrySet().iterator();
		while(itMult.hasNext()) {
			Entry<Integer, Integer> en = itMult.next();
			if(en.getValue().intValue()>1) {
				mapVarDefStatements.put(en.getKey(), stat);
			}
		}
		
		mapStatementVars.put(stat.id, set);
		
		return set;
	}

	private List<VarExprent> getAllVars(List<Exprent> lst) {
		
		List<VarExprent> res = new ArrayList<VarExprent>();
		List<Exprent> listTemp = new ArrayList<Exprent>();
		
		for(Exprent expr: lst) {
			listTemp.addAll(expr.getAllExprents(true));
			listTemp.add(expr);
		}

		for(Exprent exprent: listTemp) {
			if(exprent.type == Exprent.EXPRENT_VAR) {
				res.add((VarExprent)exprent);
			}
		}
		
		return res;
	}
	
	private boolean setDefinition(Exprent expr, Integer index) {
		if(expr.type == Exprent.EXPRENT_ASSIGNMENT) {
			Exprent left = ((AssignmentExprent)expr).getLeft();
			if(left.type == Exprent.EXPRENT_VAR) {
				VarExprent var = (VarExprent)left;
				if(var.getIndex() == index.intValue()) {
					var.setDefinition(true);
					return true;
				}
			}
		}
		return false;
	}

}
