import javax.swing.JButton;
import java.awt.*;
import java.io.Serial;

public class NButton extends JButton {
	@Serial
	private static final long serialVersionUID = -1082059330878801135L;
	private final Point c;
	
	public NButton(String name, Point c) {
		super(name);
		this.c = c;
	}
	
	public Point getPoint() {
		return c;
	}
}
