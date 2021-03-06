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

package de.fernflower.struct.lazy;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.attr.StructGeneralAttribute;
import de.fernflower.struct.consts.ConstantPool;
import de.fernflower.util.DataInputFullStream;

public class LazyLoader {

	private HashMap<String, Link> mapClassLinks = new HashMap<String, Link>(); 
	
	private IBytecodeProvider provider;
	
	public LazyLoader(IBytecodeProvider provider) {
		this.provider = provider;
	}
	
	public void addClassLink(String classname, Link link) {
		mapClassLinks.put(classname, link);
	}

	public void removeClassLink(String classname) {
		mapClassLinks.remove(classname);
	}
	
	public Link getClassLink(String classname) {
		return mapClassLinks.get(classname);
	}
	
	
	public ConstantPool loadPool(String classname) {

		try {
			
			DataInputFullStream in = getClassStream(classname); 
			if(in == null) {
				return null;
			}
			
			in.skip(8);
			
			return new ConstantPool(in); 
			
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public byte[] loadBytecode(StructMethod mt, int code_fulllength) {
		
		try {

			DataInputFullStream in = getClassStream(mt.getClassStruct().qualifiedName); 
			if(in == null) {
				return null;
			}

			byte[] res = null;
			
			in.skip(8);

			ConstantPool pool = mt.getClassStruct().getPool(); 
			if(pool == null) {
				pool = new ConstantPool(in); 
			} else {
				ConstantPool.skipPool(in);
			}

			in.skip(2);
		    int this_class = in.readUnsignedShort();
			in.skip(2);
			
			// interfaces
			in.skip(in.readUnsignedShort() * 2);

			// fields
			int size = in.readUnsignedShort();
			for (int i = 0; i < size; i++) {
				in.skip(6);
				skipAttributes(in);
			}

			// methods
			size = in.readUnsignedShort();
			for (int i = 0; i < size; i++) {
				in.skip(2);

				int name_index = in.readUnsignedShort();
				int descriptor_index = in.readUnsignedShort();

				String elem_arr[] = pool.getClassElement(ConstantPool.METHOD, this_class, name_index, descriptor_index);
				String name = elem_arr[0];
				
				if(mt.getName().equals(name)) {
					String descriptor = elem_arr[1];
					if(mt.getDescriptor().equals(descriptor)) {

						int len = in.readUnsignedShort();
						for(int j=0;j<len;j++) {

							int attr_nameindex = in.readUnsignedShort();
							String attrname = pool.getPrimitiveConstant(attr_nameindex).getString();

							if(StructGeneralAttribute.ATTRIBUTE_CODE.equals(attrname)) {
								in.skip(12);

								res = new byte[code_fulllength];
								in.readFull(res);
								return res;
							} else {
								in.skip(in.readInt());
							}
						}

						return null;
					}
				}

				skipAttributes(in);
			}

			return null;

		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
		
	}

	public DataInputFullStream getClassStream(String externPath, String internPath) throws IOException {
		InputStream instream = provider.getBytecodeStream(externPath, internPath);
		return instream == null?null:new DataInputFullStream(instream);
	}

	public DataInputFullStream getClassStream(String qualifiedClassName) throws IOException {
		Link link = mapClassLinks.get(qualifiedClassName);
		return link == null?null:getClassStream(link.externPath, link.internPath);
	}
	
	private void skipAttributes(DataInputFullStream in) throws IOException {

		int length = in.readUnsignedShort();
	    for (int i = 0; i < length; i++) {
	    	in.skip(2);
	    	in.skip(in.readInt());
	    }
	    
	}

	
	public static class Link {
		
		public static final int CLASS = 1; 
		public static final int ENTRY = 2; 
		
		public int type;
		public String externPath;
		public String internPath;
		
		public Link(int type, String externPath, String internPath) {
			this.type = type;
			this.externPath = externPath;
			this.internPath = internPath;
		}
	}
	
}
