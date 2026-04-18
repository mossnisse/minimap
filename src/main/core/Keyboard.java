package main.core;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.HashMap;

public class Keyboard {
	private static final boolean[] key_down = new boolean[256];
	private static final HashMap<Key, String> accelerators = new HashMap<Key, String>();

	public static class Key {
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
			return "keyCode: " + keyCode + " modifiers: " + modifiers;
		}
	}
	
	public static void addAccelerator(Key key, String name) {
		accelerators.put(key, name);
	}
	
	public static void activate() {
		KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

		// The existing Dispatcher
		focusManager.addKeyEventDispatcher(ke -> {
			if (ke.getSource() instanceof javax.swing.text.JTextComponent) {
				return false;
			}
			synchronized (Keyboard.class) { // Use core.Keyboard.class, not KeyListener.class
				switch (ke.getID()) {
					case KeyEvent.KEY_PRESSED -> key_down[ke.getKeyCode()] = true;
					case KeyEvent.KEY_RELEASED -> key_down[ke.getKeyCode()] = false;
				}
				return false;
			}
		});

		focusManager.addPropertyChangeListener("focusedWindow", evt -> {
			if (evt.getNewValue() == null) {
				// Focus left the app entirely - clear all keys!
				synchronized (Keyboard.class) {
					java.util.Arrays.fill(key_down, false);
				}
			}
		});
		
		addAccelerator(new Key(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "Search" );
		addAccelerator(new Key(KeyEvent.VK_RIGHT), "Next" );
		addAccelerator(new Key(KeyEvent.VK_LEFT), "Prev" );
		addAccelerator(new Key(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "copyLastB" );
	}
	
	public static boolean isKeyDown(int keyCode) {
		return key_down[keyCode];
	}
}
