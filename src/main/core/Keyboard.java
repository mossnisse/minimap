package main.core;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.BitSet;

public class Keyboard {
	private static final BitSet key_down = new BitSet();

	public static void activate() {
		KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

		// The existing Dispatcher
		focusManager.addKeyEventDispatcher(ke -> {
			if (ke.getSource() instanceof javax.swing.text.JTextComponent) {
				return false;
			}
			synchronized (Keyboard.class) { // Use core.Keyboard.class, not KeyListener.class
				switch (ke.getID()) {
					case KeyEvent.KEY_PRESSED -> key_down.set(ke.getKeyCode());
					case KeyEvent.KEY_RELEASED -> key_down.clear(ke.getKeyCode());
				}
				return false;
			}
		});

		focusManager.addPropertyChangeListener("focusedWindow", evt -> {
			if (evt.getNewValue() == null) {
				// Focus left the app entirely - clear all keys!
				synchronized (Keyboard.class) {
					key_down.clear();
				}
			}
		});
	}
	
	public static boolean isKeyDown(int keyCode) {
		return key_down.get(keyCode);
	}
}