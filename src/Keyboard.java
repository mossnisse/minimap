
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;

public class Keyboard {
	private static final boolean[] key_down = new boolean[256];
	private static final ArrayList<NActionListener> actionListeners = new ArrayList<NActionListener>();
	private static final HashMap<Key, String> accelerators = new HashMap<Key, String>();
	
	public static void addActionListener(NActionListener al) {
		actionListeners.add(al);
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
			synchronized (Keyboard.class) { // Use Keyboard.class, not KeyListener.class
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
					System.out.println("Focus lost: Clearing all keys.");
				}
			}
		});
		
		addAccelerator(new Key(KeyEvent.VK_F, KeyEvent.CTRL_MASK), "Search" );
		addAccelerator(new Key(KeyEvent.VK_RIGHT), "Next" );
		addAccelerator(new Key(KeyEvent.VK_LEFT), "Prev" );
		addAccelerator(new Key(KeyEvent.VK_L, KeyEvent.CTRL_MASK), "copyLastB" );
	}
	
	public static boolean isKeyDown(int keyCode) {
		return key_down[keyCode];
	}
}
