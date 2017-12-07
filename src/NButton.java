import geometry.Point;

import javax.swing.JButton;

public class NButton extends JButton {

	private static final long serialVersionUID = -1082059330878801135L;
	private Point c;
	
	public NButton(String name, Point c) {
		super(name);
		this.c = c;
	}
	
	public Point getPoint() {
		return c;
	}
}
