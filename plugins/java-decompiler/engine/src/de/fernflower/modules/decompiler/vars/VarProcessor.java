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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.VarNamesCollector;
import de.fernflower.modules.decompiler.stats.RootStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.VarType;


public class VarProcessor {

	private HashMap<VarVersionPaar, String> mapVarNames = new HashMap<VarVersionPaar, String>();

	private VarVersionsProcessor varvers;
	
	private HashMap<VarVersionPaar, String> thisvars = new HashMap<VarVersionPaar, String>();

	private HashSet<VarVersionPaar> externvars = new HashSet<VarVersionPaar>();
	
	public void setVarVersions(RootStatement root) {
		
		varvers = new VarVersionsProcessor();
		varvers.setVarVersions(root);
	}
	
	public void setVarDefinitions(Statement root) {
		mapVarNames = new HashMap<VarVersionPaar, String>();
		
		VarDefinitionHelper defproc = new VarDefinitionHelper(root, 
				(StructMethod)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD), this); 
		defproc.setVarDefinitions();
	}
	
	public void setDebugVarNames(HashMap<Integer, String> mapDebugVarNames) {

		if(varvers == null) {
			return;
		}
		
		HashMap<Integer, Integer> mapOriginalVarIndices = varvers.getMapOriginalVarIndices();
		
		List<VarVersionPaar> listVars = new ArrayList<VarVersionPaar>(mapVarNames.keySet());
		Collections.sort(listVars, new Comparator<VarVersionPaar>() {
			public int compare(VarVersionPaar o1, VarVersionPaar o2) {
				return o1.var>o2.var?1:(o1.var==o2.var?0:-1);
			}
		});
		
		HashMap<String, Integer> mapNames = new HashMap<String, Integer>(); 
		
		for(VarVersionPaar varpaar : listVars) {
			String name = mapVarNames.get(varpaar);
			
			Integer orindex = mapOriginalVarIndices.get(varpaar.var);
			if(orindex != null && mapDebugVarNames.containsKey(orindex)) {
				name = mapDebugVarNames.get(orindex);
			}

			Integer counter = mapNames.get(name);
			mapNames.put(name, counter==null?counter = new Integer(0):++counter);
			
			if(counter > 0) {
				name+=String.valueOf(counter);
			}
			
			mapVarNames.put(varpaar, name);
		}
		
	}
	
	public void refreshVarNames(VarNamesCollector vc) {
		
		HashMap<VarVersionPaar, String> tempVarNames = new HashMap<VarVersionPaar, String>(mapVarNames); 
		for(Entry<VarVersionPaar, String> ent: tempVarNames.entrySet()) {
			mapVarNames.put(ent.getKey(), vc.getFreeName(ent.getValue()));
		}
	}
	
	
	public VarType getVarType(VarVersionPaar varpaar) {
		return varvers==null?null:varvers.getVarType(varpaar);
	}

	public void setVarType(VarVersionPaar varpaar, VarType type) {
		varvers.setVarType(varpaar, type);
	}
	
	public String getVarName(VarVersionPaar varpaar) {
		return mapVarNames==null?null:mapVarNames.get(varpaar);
	}
	
	public void setVarName(VarVersionPaar varpaar, String name) {
		mapVarNames.put(varpaar, name);
	}
	
	public int getVarFinal(VarVersionPaar varpaar) {
		return varvers==null?VarTypeProcessor.VAR_FINAL:varvers.getVarFinal(varpaar);
	}
	
	public void setVarFinal(VarVersionPaar varpaar, int finaltype) {
		varvers.setVarFinal(varpaar, finaltype);
	}

	public HashMap<VarVersionPaar, String> getThisvars() {
		return thisvars;
	}

	public HashSet<VarVersionPaar> getExternvars() {
		return externvars;
	}

}
