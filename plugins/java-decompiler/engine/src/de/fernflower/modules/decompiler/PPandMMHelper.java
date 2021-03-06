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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.sforms.DirectNode;
import de.fernflower.modules.decompiler.sforms.FlattenStatementsHelper;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.struct.gen.VarType;

public class PPandMMHelper {

	private boolean exprentReplaced;
	
	public boolean findPPandMM(RootStatement root) {
		
		FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
		DirectGraph dgraph = flatthelper.buildDirectGraph(root);

		LinkedList<DirectNode> stack = new LinkedList<DirectNode>();
		stack.add(dgraph.first);
		
		HashSet<DirectNode> setVisited = new HashSet<DirectNode>(); 
		
		boolean res = false;
		
		while(!stack.isEmpty()) {
			
			DirectNode node = stack.removeFirst();
			
			if(setVisited.contains(node)) {
				continue;
			}
			setVisited.add(node);
			
			res |= processExprentList(node.exprents);
			
			stack.addAll(node.succs);
		}
		
		return res;
	}
	
	private boolean processExprentList(List<Exprent> lst) {
		
		boolean result = false;

		for(int i=0;i<lst.size();i++) {
			Exprent exprent = lst.get(i);
			exprentReplaced = false;
			
			Exprent retexpr = processExprentRecursive(exprent);
			if(retexpr != null) {
				lst.set(i, retexpr);

				result = true;
				i--; // process the same exprent again
			}
			
			result |= exprentReplaced;
		}

		return result;
	}

	private Exprent processExprentRecursive(Exprent exprent) {

		boolean replaced = true;
		while(replaced) {
			replaced = false;

			for(Exprent expr: exprent.getAllExprents()) {
				Exprent retexpr = processExprentRecursive(expr);
				if(retexpr != null) {
					exprent.replaceExprent(expr, retexpr);
					replaced = true;
					exprentReplaced = true;
					break;
				}
			}
		}

		if(exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent as = (AssignmentExprent)exprent;

			if(as.getRight().type == Exprent.EXPRENT_FUNCTION) {
				FunctionExprent func = (FunctionExprent)as.getRight();

				VarType midlayer = null;
				if(func.getFunctype() >= FunctionExprent.FUNCTION_I2L && 
						func.getFunctype() <= FunctionExprent.FUNCTION_I2S) {
					midlayer = func.getSimpleCastType();
					if(func.getLstOperands().get(0).type == Exprent.EXPRENT_FUNCTION) {
						func = (FunctionExprent)func.getLstOperands().get(0);
					} else {
						return null;
					}
				}

				if(func.getFunctype() == FunctionExprent.FUNCTION_ADD ||
						func.getFunctype() == FunctionExprent.FUNCTION_SUB) {
					Exprent econd = func.getLstOperands().get(0);
					Exprent econst = func.getLstOperands().get(1);

					if(econst.type != Exprent.EXPRENT_CONST && econd.type == Exprent.EXPRENT_CONST &&
							func.getFunctype() == FunctionExprent.FUNCTION_ADD) {
						econd = econst;
						econst = func.getLstOperands().get(0);
					}

					if(econst.type == Exprent.EXPRENT_CONST && ((ConstExprent)econst).hasValueOne()) {
						Exprent left = as.getLeft();

						VarType condtype = econd.getExprType();
						if(left.equals(econd) && (midlayer == null || midlayer.equals(condtype))) {
							FunctionExprent ret = new FunctionExprent(
									func.getFunctype() == FunctionExprent.FUNCTION_ADD?FunctionExprent.FUNCTION_PPI:FunctionExprent.FUNCTION_MMI,
											Arrays.asList(new Exprent[]{econd}));
							ret.setImplicitType(condtype);

							exprentReplaced = true;
							return ret;
						}
					}
				}
			}
		}

		return null;
	}
	
}
