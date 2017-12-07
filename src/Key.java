import java.awt.event.KeyEvent;


public class Key {
	private int keyCode;
	private int modifiers;
	
	Key(int keyCode, int modifiers) {
		this.keyCode = keyCode;
		this.modifiers = modifiers;
	}
	
	Key(int keyCode) {
		this.keyCode = keyCode;
		this.modifiers = 0;
	}
	
	Key(KeyEvent ke) {
		this.keyCode = ke.getKeyCode();
		this.modifiers = ke.getModifiers();
	}
	
	@Override
	public boolean equals(Object k) {
		return ((Key)k).getKeyCode() == this.keyCode;// && ((Key)k).modifiers == modifiers;
	}
	
	@Override
	public int hashCode() {
		return keyCode * 16 + modifiers;
	}
	
	public boolean equals(Key k) {
		return k.keyCode == this.keyCode;// && ((Key)k).modifiers == modifiers;
	}
	
	public boolean equals(KeyEvent k) {
		return k.getKeyCode() == this.keyCode;// && k.getModifiers() == modifiers;
	}

	public int getKeyCode() {
		return keyCode;
	}
	
	public int getModifiers() {
		return modifiers;
	}
	
	public String toString() {
		return "keyCode: "+Integer.toString(keyCode) + " modifiers: "+Integer.toString(modifiers); 
	}
	
	
}


