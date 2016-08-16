import ij.*;
import java.awt.*;
import ij.plugin.*;


public class Close_AB implements PlugIn {

	public void run(String s) {
		String arg = Macro.getOptions();

		if (arg == null && s.equals(""))  return;
		if (arg != null) {
		IJ.selectWindow(arg.trim());
		Frame frame = WindowManager.getFrontWindow();
		if (frame instanceof javax.swing.JFrame) {
			((javax.swing.JFrame)frame).setVisible(false);
			((javax.swing.JFrame)frame).setTitle("xxxx");
			((javax.swing.JFrame)frame).dispose();
			WindowManager.removeWindow(frame);
		}

		}

	}

}
