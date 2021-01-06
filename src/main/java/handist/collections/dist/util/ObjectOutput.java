package handist.collections.dist.util;

import java.io.OutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import apgas.impl.KryoSerializer;

public class ObjectOutput {
	
	final OutputStream stream;
	final Output output;
	final Kryo kryo;
	
	private boolean isClosed = false;	
	
	
	public ObjectOutput(OutputStream out) {
		if (out == null) {
			throw new NullPointerException();
		}
		stream = out;
		output = new Output(stream);
		kryo = KryoSerializer.kryoThreadLocal.get();
		kryo.reset();		
		kryo.setAutoReset(false);	
	}	
	
	public void writeObject(Object obj) {
		if(isClosed)
			throw new RuntimeException(this + " has closed.");
		kryo.writeClassAndObject(output, obj);		
	}
	
	public void writeInt(int val) {
		if(isClosed)
			throw new RuntimeException(this + " has closed.");
		output.writeInt(val);
	}
	
	public void writeLong(long val) {		
		if(isClosed)
			throw new RuntimeException(this + " has closed.");
		output.writeLong(val);
	}
	
	public void writeByte(byte val) {		
		if(isClosed)
			throw new RuntimeException(this + " has closed.");
		output.writeByte(val);
	}
	
	public void close() {		
		output.close();
		kryo.reset();
		kryo.setAutoReset(true);	// Need for using at remote place. 
		isClosed = true;
	}
	
	public void flush() {
		output.flush();
	}
	
	public void reset() {	
		kryo.reset();
	}
	
	public void setAutoReset(boolean autoReset) {
		if(isClosed)
			return;
		kryo.setAutoReset(autoReset);
	}
}