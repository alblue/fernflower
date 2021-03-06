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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.sforms.DirectGraph;
import de.fernflower.modules.decompiler.sforms.FlattenStatementsHelper;
import de.fernflower.modules.decompiler.sforms.SSAConstructorSparseEx;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.FastSparseSetFactory.FastSparseSet;

public class VarVersionsProcessor {
	
	private HashMap<Integer, Integer> mapOriginalVarIndices = new HashMap<Integer, Integer>();
	
	private VarTypeProcessor typeproc;
	
	public void setVarVersions(RootStatement root) {
		
		StructMethod mt = (StructMethod)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD);
		
		SSAConstructorSparseEx ssa = new SSAConstructorSparseEx();
		ssa.splitVariables(root, mt);

		FlattenStatementsHelper flatthelper = new FlattenStatementsHelper();
		DirectGraph dgraph = flatthelper.buildDirectGraph(root);
		
//		System.out.println("~~~~~~~~~~~~~~~~~~~~~~ \r\n"+root.toJava());
		
		mergePhiVersions(ssa, dgraph);

//		System.out.println("~~~~~~~~~~~~~~~~~~~~~~ \r\n"+root.toJava());
		
		typeproc = new VarTypeProcessor();
		typeproc.calculateVarTypes(root, dgraph);
		
		simpleMerge(typeproc, dgraph, mt);
		
		// FIXME: advanced merging
		
		eliminateNonJavaTypes(typeproc);
		
		setNewVarIndices(typeproc, dgraph);
	}
	
	private void mergePhiVersions(SSAConstructorSparseEx ssa, DirectGraph dgraph) {
		
		// collect phi versions
		List<HashSet<VarVersionPaar>> lst = new ArrayList<HashSet<VarVersionPaar>>(); 
		for(Entry<VarVersionPaar, FastSparseSet<Integer>> ent: ssa.getPhi().entrySet()) {
			HashSet<VarVersionPaar> set = new HashSet<VarVersionPaar>();
			set.add(ent.getKey());
			for(Integer vers: ent.getValue()) {
				set.add(new VarVersionPaar(ent.getKey().var, vers.intValue()));
			}
			
			for(int i=lst.size()-1;i>=0;i--) {
				HashSet<VarVersionPaar> tset = lst.get(i);
				HashSet<VarVersionPaar> intersection = new HashSet<VarVersionPaar>(set);
				intersection.retainAll(tset);
				
				if(!intersection.isEmpty()) {
					set.addAll(tset);
					lst.remove(i);
				}
			}
			
			lst.add(set);
		}
		
		final HashMap<VarVersionPaar, Integer> phivers = new HashMap<VarVersionPaar, Integer>(); 
		for(HashSet<VarVersionPaar> set: lst) {
			int min = Integer.MAX_VALUE;
			for(VarVersionPaar paar: set) {
				if(paar.version<min) {
					min = paar.version;
				}
			}
			
			for(VarVersionPaar paar: set) {
				phivers.put(new VarVersionPaar(paar.var, paar.version), min);
			}
		}
		
		
		dgraph.iterateExprents(new DirectGraph.ExprentIterator() {
			public int processExprent(Exprent exprent) {
				List<Exprent> lst = exprent.getAllExprents(true);
				lst.add(exprent);
				
				for(Exprent expr: lst) {
					if(expr.type == Exprent.EXPRENT_VAR) {
						VarExprent var = (VarExprent)expr;
						Integer vers = phivers.get(new VarVersionPaar(var));
						if(vers != null) {
							var.setVersion(vers);
						}
					}
				}
				return 0;
			}
		});
		
	}
	
	private void eliminateNonJavaTypes(VarTypeProcessor typeproc) {
		
		HashMap<VarVersionPaar, VarType> mapExprentMaxTypes = typeproc.getMapExprentMaxTypes();
		HashMap<VarVersionPaar, VarType> mapExprentMinTypes = typeproc.getMapExprentMinTypes();
		
		HashSet<VarVersionPaar> set = new HashSet<VarVersionPaar>(mapExprentMinTypes.keySet());
		for(VarVersionPaar paar: set) {
			VarType type = mapExprentMinTypes.get(paar);
			VarType maxtype = mapExprentMaxTypes.get(paar);
			
			if(type.type == CodeConstants.TYPE_BYTECHAR || type.type == CodeConstants.TYPE_SHORTCHAR) {
				if(maxtype != null && maxtype.type == CodeConstants.TYPE_CHAR) {
					type = VarType.VARTYPE_CHAR;
				} else {
					type = type.type == CodeConstants.TYPE_BYTECHAR?VarType.VARTYPE_BYTE:VarType.VARTYPE_SHORT;
				}
				mapExprentMinTypes.put(paar, type);
			//} else if(type.type == CodeConstants.TYPE_CHAR && (maxtype == null || maxtype.type == CodeConstants.TYPE_INT)) { // when possible, lift char to int 
			//	mapExprentMinTypes.put(paar, VarType.VARTYPE_INT);
			} else if(type.type == CodeConstants.TYPE_NULL) {
				mapExprentMinTypes.put(paar, VarType.VARTYPE_OBJECT);
			}
		}
		
	}
	
	private void simpleMerge(VarTypeProcessor typeproc, DirectGraph dgraph, StructMethod mt) {
		
		HashMap<VarVersionPaar, VarType> mapExprentMaxTypes = typeproc.getMapExprentMaxTypes();
		HashMap<VarVersionPaar, VarType> mapExprentMinTypes = typeproc.getMapExprentMinTypes();
		
		HashMap<Integer, HashSet<Integer>> mapVarVersions = new HashMap<Integer, HashSet<Integer>>();
		
		for(VarVersionPaar varpaar: mapExprentMinTypes.keySet()) {
			if(varpaar.version >= 0) {  // don't merge constants
				HashSet<Integer> set = mapVarVersions.get(varpaar.var);
				if(set == null) {
					set = new HashSet<Integer>();
					mapVarVersions.put(varpaar.var, set);
				}
				set.add(varpaar.version);
			}
		}
		
		boolean is_method_static = (mt.getAccessFlags() & CodeConstants.ACC_STATIC) != 0;
		
		final HashMap<VarVersionPaar, Integer> mapMergedVersions = new HashMap<VarVersionPaar, Integer>();
		
		for(Entry<Integer, HashSet<Integer>> ent: mapVarVersions.entrySet()) {
			
			if(ent.getValue().size() > 1) {
				List<Integer> lstVersions = new ArrayList<Integer>(ent.getValue());
				Collections.sort(lstVersions);
				
				for(int i=0;i<lstVersions.size();i++) {
					VarVersionPaar firstpaar = new VarVersionPaar(ent.getKey(), lstVersions.get(i));
					VarType firsttype = mapExprentMinTypes.get(firstpaar);
					
					if(firstpaar.var == 0 && firstpaar.version == 1 && !is_method_static) {
						continue; // don't merge 'this' variable
					}
					
					for(int j=i+1;j<lstVersions.size();j++) {
						VarVersionPaar secpaar = new VarVersionPaar(ent.getKey(), lstVersions.get(j));
						VarType sectype = mapExprentMinTypes.get(secpaar);
						
						if(firsttype.equals(sectype) || (firsttype.equals(VarType.VARTYPE_NULL) && sectype.type == CodeConstants.TYPE_OBJECT) 
								|| (sectype.equals(VarType.VARTYPE_NULL) && firsttype.type == CodeConstants.TYPE_OBJECT)) {

							VarType firstMaxType = mapExprentMaxTypes.get(firstpaar); 
							VarType secMaxType = mapExprentMaxTypes.get(secpaar); 
							mapExprentMaxTypes.put(firstpaar, firstMaxType==null?secMaxType:
										(secMaxType==null?firstMaxType:VarType.getCommonMinType(firstMaxType, secMaxType)));
							
							
							mapMergedVersions.put(secpaar, firstpaar.version);
							mapExprentMaxTypes.remove(secpaar);
							mapExprentMinTypes.remove(secpaar);
							
							if(firsttype.equals(VarType.VARTYPE_NULL)) {
								mapExprentMinTypes.put(firstpaar, sectype);
								firsttype = sectype;
							}
							
							typeproc.getMapFinalVars().put(firstpaar, VarTypeProcessor.VAR_NONFINAL);
							
							lstVersions.remove(j);
							j--;
						}
						
					}
				}
			}
		}

		if(!mapMergedVersions.isEmpty()) {
			dgraph.iterateExprents(new DirectGraph.ExprentIterator() {
				public int processExprent(Exprent exprent) {
					List<Exprent> lst = exprent.getAllExprents(true);
					lst.add(exprent);
					
					for(Exprent expr: lst) {
						if(expr.type == Exprent.EXPRENT_VAR) {
							VarExprent varex = (VarExprent)expr;
							Integer newversion = mapMergedVersions.get(new VarVersionPaar(varex));
							if(newversion != null) {
								varex.setVersion(newversion);
							}
						}
					}
					
					return 0;
				}
			});
		}
		
	}
	
	private void setNewVarIndices(VarTypeProcessor typeproc, DirectGraph dgraph) {
		
		final HashMap<VarVersionPaar, VarType> mapExprentMaxTypes = typeproc.getMapExprentMaxTypes();
		HashMap<VarVersionPaar, VarType> mapExprentMinTypes = typeproc.getMapExprentMinTypes();
		HashMap<VarVersionPaar, Integer> mapFinalVars = typeproc.getMapFinalVars();
		
		CounterContainer ccon = DecompilerContext.getCountercontainer();
		
		final HashMap<VarVersionPaar, Integer> mapVarPaar = new HashMap<VarVersionPaar, Integer>(); 
		HashMap<Integer, Integer> mapOriginalVarIndices = new HashMap<Integer, Integer>();
		
		// map var-version paars on new var indexes
		HashSet<VarVersionPaar> set = new HashSet<VarVersionPaar>(mapExprentMinTypes.keySet());
		for(VarVersionPaar vpaar: set) {
			
			if(vpaar.version >= 0) {
				int newindex = vpaar.version == 1?vpaar.var:
						ccon.getCounterAndIncrement(CounterContainer.VAR_COUNTER);  
			
				VarVersionPaar newvar = new VarVersionPaar(newindex, 0);
				
				mapExprentMinTypes.put(newvar, mapExprentMinTypes.get(vpaar));
				mapExprentMaxTypes.put(newvar, mapExprentMaxTypes.get(vpaar));
				
				if(mapFinalVars.containsKey(vpaar)) {
					mapFinalVars.put(newvar, mapFinalVars.remove(vpaar));
				}
				
				mapVarPaar.put(vpaar, newindex);
				mapOriginalVarIndices.put(newindex, vpaar.var);
			}
		}

		// set new vars
		dgraph.iterateExprents(new DirectGraph.ExprentIterator() {
			public int processExprent(Exprent exprent) {
				List<Exprent> lst = exprent.getAllExprents(true);
				lst.add(exprent);
				
				for(Exprent expr: lst) {
					if(expr.type == Exprent.EXPRENT_VAR) {
						VarExprent varex = (VarExprent)expr;
						Integer newvarindex = mapVarPaar.get(new VarVersionPaar(varex));
						if(newvarindex != null) {
							varex.setIndex(newvarindex);
							varex.setVersion(0);
						}
					} else if(expr.type == Exprent.EXPRENT_CONST) {
						VarType maxType = mapExprentMaxTypes.get(new VarVersionPaar(expr.id, -1));
						if(maxType != null && maxType.equals(VarType.VARTYPE_CHAR)) {
							((ConstExprent)expr).setConsttype(maxType);
						}
					}
				}
				
				return 0;
			}
		});
		
		this.mapOriginalVarIndices = mapOriginalVarIndices;
	}
	
	public VarType getVarType(VarVersionPaar varpaar) {
		return typeproc==null?null:typeproc.getVarType(varpaar);
	}
	
	public void setVarType(VarVersionPaar varpaar, VarType type) {
		typeproc.setVarType(varpaar, type);
	}

	public int getVarFinal(VarVersionPaar varpaar) {
		
		int ret = VarTypeProcessor.VAR_FINAL;
		if(typeproc!=null) {
			Integer fin = typeproc.getMapFinalVars().get(varpaar);
			ret = fin==null?VarTypeProcessor.VAR_FINAL:fin.intValue();
		}
		
		return ret;
	}
	
	public void setVarFinal(VarVersionPaar varpaar, int finaltype) {
		typeproc.getMapFinalVars().put(varpaar, finaltype);
	}

	public HashMap<Integer, Integer> getMapOriginalVarIndices() {
		return mapOriginalVarIndices;
	}

	
}
