import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.HashMap;

public class Keyboard {
	public static Keyboard extatic = new Keyboard();
	private static boolean[] key_down = new boolean[256];
	private static ArrayList<NActionListener> actionListeners = new ArrayList<NActionListener>();
	private static HashMap<Key, String> accelerators = new HashMap<Key, String>();
	
	public static void addActionListener(NActionListener al) {
		actionListeners.add(al);
	}
	
	public static void addAccelerator(Key key, String name) {
		accelerators.put(key, name);
	}
	
	public static void activate() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent ke) {
                synchronized (KeyListener.class) {
                    switch (ke.getID()) {
                    case KeyEvent.KEY_PRESSED:
                        	key_down[ke.getKeyCode()] = true;
                        	//System.out.println(new Key(ke)); 
                        	//System.out.println(accelerators);
                        	if (Keyboard.accelerators.containsKey(new Key(ke))) {
                        		System.out.println("Fire in the hole: "+ke+"\naction: "+accelerators.get(ke.getKeyCode()));
                        		 for (NActionListener al: actionListeners ) {
                        			 //System.out.println(actionListeners);
                        			 al.nActionPerformed(accelerators.get(new Key(ke)));
                        		 }
                        	}	
                        break;
                    case KeyEvent.KEY_RELEASED:
                        	key_down[ke.getKeyCode()] = false;
                        break;
                    }
                    return false;
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
	
	public static boolean[] getKeys() {
		return key_down;
	}
}
