import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.macro.*;

import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.*;
import javax.swing.plaf.metal.*;

import java.util.*;

import bsh.EvalError;
import bsh.Interpreter;

import java.lang.Runnable; 
import java.lang.Thread; 


/**
* @author Jerome Mutterer
* @author Michael Schmid
* date : 2015 09 15
* version no : 203
* 
*/

public class Action_Bar implements PlugIn, ActionListener, DropTargetListener, Runnable {
	
	public static final String VERSION = "2.04";
	Interpreter bsh;
	String name, title, path;
	String startupAction = "";
	String codeLibrary = "";
	String DnDAction = "";
	String separator = System.getProperty("file.separator");
	JFrame frame = new JFrame();
	Frame frontframe;
	int xfw = 0;
	int yfw = 0;
	int wfw = 0;
	int hfw = 0;
	JToolBar toolBar = null;
	boolean tbOpenned = false;
	boolean grid = true;
	boolean visible = true;
	boolean shouldExit = false;
	JButton button = null;
	private boolean isPopup = false;
	private boolean captureMenus = true;
	private boolean isSticky = false;
	private boolean somethingWentWrong = false;
	int nButtons = 0;
	private Iterator iterator;
	protected static String ijHome = IJ.versionLessThan("1.43e") ?
	IJ.getDirectory("plugins") + "../" :
	IJ.getDirectory("imagej");
	Color[] presetColors = { new Color(192,192,192), new Color(213,170,213),
 	new Color(170,170,255), new Color(170,213,255), new Color(170,213,170),
	new Color(255,255,170), new Color(250,224,175), new Color(255,170,170) };
	private String imports;
	private String bshPath;

	
	public void run(String s) {
		// s used if called from another plugin, or from an installed command.
		// arg used when called from a run("command", arg) macro function
		// if both are empty, we choose to run the assistant "createAB.txt"
		
        if (s.equals("about")) {
            showAbout();
            return;
        }
		String arg = Macro.getOptions();
		
		if (arg == null && s.equals("")) {
			try {
				//File macro = new File(Action_Bar.class.getResource(
				//	"createAB.txt").getFile());
				// new MacroRunner(macro);
				runMacro("createAB.txt");
				return;
			} catch (Exception e) {
				IJ.error("createAB.txt file not found");
                return;
			}
			
		} else if (arg == null) { // call from an installed command
			path = getURL(s);
			//path = ijHome + s;
			try {
				name = path.substring(path.lastIndexOf(File.separator) + 1);
			} catch (Exception e) {
			}
		} else { // called from a macro by run("Action Bar",arg)
			path = getURL(arg.trim());
			try {
				if (path.endsWith(".txt"))
					path = path.substring(0, path.indexOf(".txt") + 4);
				name = path.substring(path.lastIndexOf("/") + 1);
			} catch (Exception e) {
			}
		}
		
		// title = name.substring(0, name.indexOf("."));
		title = name.substring((name.lastIndexOf("/")>0)?name.lastIndexOf("/")+1:0, name.lastIndexOf(".")).replaceAll("_", " ")
		.trim();
		
		if (IJ.isMacintosh()) try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		}	
		catch(Exception e) {}
		
		frame.setTitle(title);
		
		if (WindowManager.getFrame(title) != null) {
			WindowManager.getFrame(title).toFront();
			return;
		}
		// this listener will save the bar's position and close it.
		frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					rememberXYlocation();
					e.getWindow().dispose();
				}
				public void windowClosed(WindowEvent e) {
					WindowManager.removeWindow((Frame) frame);
				}
                public void windowActivated(WindowEvent e) {
                    if (IJ.isMacintosh()&&captureMenus) { // have Macintosh menu bar while the action bar is in the foreground
                        ImageJ ij = IJ.getInstance();
                        if (ij != null && !ij.quitting()) {
                            IJ.wait(10);
                            frame.setMenuBar(Menus.getMenuBar());
                        }
                    }
                }
		});
		frontframe = WindowManager.getFrontWindow();
		if (frontframe != null){
			xfw = frontframe.getLocation().x;
			yfw = frontframe.getLocation().y;
			wfw = frontframe.getWidth();
			hfw = frontframe.getHeight();
		}
		// toolbars will be added as lines in a n(0) rows 1 column layout
		frame.getContentPane().setLayout(new GridLayout(0, 1));
		
		// sets the bar's default icon to imagej icon
		frame.setIconImage(IJ.getInstance().getIconImage());
		
		// read the config file, and add toolbars to the frame
		designPanel();
		
		// captures the ImageJ KeyListener
		frame.setFocusable(true);
		frame.addKeyListener(IJ.getInstance());
		
		// setup the frame, and display it
		frame.setResizable(false);
		
		if (!isPopup) {
			frame.setLocation((int) Prefs
				.get("actionbar" + title + ".xloc", 10), (int) Prefs.get(
					"actionbar" + title + ".yloc", 10));
			WindowManager.addWindow(frame);
        } else {
			frame.setLocation(MouseInfo.getPointerInfo().getLocation());
			frame.setUndecorated(true);
			frame.addKeyListener(new KeyListener() {
					public void keyReleased(KeyEvent e) {
					}
					
					public void keyTyped(KeyEvent e) {
					}
					
					public void keyPressed(KeyEvent e) {
						int code = e.getKeyCode();
						if (code == KeyEvent.VK_ESCAPE) {
							frame.dispose();
							WindowManager.removeWindow(frame);
						}
					}
			});
		}
		
		if (isSticky) {
			frame.setUndecorated(true);
		}
		frame.pack();
		frame.setVisible(true);
		
		if (somethingWentWrong) {
			closeActionBar();
			return;
		}
		if (startupAction != "")
		try {
			new MacroRunner(startupAction + "\n" + codeLibrary);
		} catch (Exception fe) {
		}
		
		if (DnDAction != "")
		try {
			// attach custom DND listener here
			new DropTarget(frame, this);
		} catch (Exception fe) {
		}
		
		if (IJ.macroRunning()!=true) WindowManager.setWindow(frontframe);
		
		if (isSticky) {
			stickToActiveWindow();
			while ((shouldExit==false)&& (frame.getTitle()!="xxxx")){
				
				if (IJ.macroRunning()!=true) try {
					
					ImageWindow fw = WindowManager.getCurrentWindow();
					if (fw == null)
						frame.setVisible(false);
					if ((fw != null) && (fw.getLocation().x != xfw)
						|| (fw.getLocation().y != yfw)
					|| (fw.getWidth() != wfw)
					|| (fw.getHeight() != hfw)) {
					xfw = fw.getLocation().x;
					yfw = fw.getLocation().y;
					wfw = fw.getWidth();
					hfw = fw.getHeight();
					stickToActiveWindow();
					}
				} catch (Exception e) {
					// TODO: handle exception
				}
				IJ.wait(20);
			}
			if (frame.getTitle()=="xxxx") closeActionBar();
			if ((shouldExit)) return;
		}
    }
		
	         protected String getURL(String path) {
	          if (path.startsWith("plugins/")) path = "/"+path;
                 if (path.startsWith("/")) {
                         String fullPath = ijHome + path;
                         if (new File(fullPath).exists())
                                 return fullPath;
                         if (path.startsWith("/plugins/"))
                                 path = path.substring(8);
                 } else if (path.startsWith("jar:file:")) {
                	 String inputFile = "jar:file:"+IJ.getDirectory("plugins")+path.substring(9);
                	 try {
						URL u = new URL(inputFile);
						return u.toString();
					} catch (MalformedURLException e) {
						IJ.error("Error getting config file ressource from JAR file.");
						e.printStackTrace();
					}
                 }
                 
                 URL url = getClass().getResource(path);
                 if (url == null) {
                         url = getClass().getResource("/ActionBar/" + path);
            if (url == null) {
                                 throw new RuntimeException("Cannot find resource '" + path + "'");
                         }
                 }
                 return url.toString();
         }
 
         protected void runMacro(String path) {
                 String url = getURL(path);
                 if (url.startsWith("jar:")) try {
                         String macro = readString(new URL(url).openStream());
                         new MacroRunner(macro);
                         return;
                 } catch (Exception e) {
                         IJ.handleException(e);
                 }
        else try {
            File macro = new File(new URL(url).toURI());
                         new MacroRunner(macro);
        } catch (Exception e) {
                IJ.handleException(e);
                 }
         }
 
         protected String readString(InputStream in) {
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                 StringBuffer buffer = new StringBuffer();
                 char[] buf = new char[16384];
                 try {
                         for (;;) {
                                 int count = reader.read(buf);
                                 if (count < 0)
                                         break;
                                 buffer.append(buf, 0, count);
                         }
                         reader.close();
                 } catch (IOException e) {
                         IJ.handleException(e);
                 }
                 return buffer.toString();
         }

	private void stickToActiveWindow() {
		ImageWindow fw = WindowManager.getCurrentWindow();
		updateButtons();
		try {
			if (fw != null) {
				if (!frame.isVisible())
					frame.setVisible(true);
				frame.toFront();
				frame.setLocation(fw.getLocation().x + fw.getWidth(), fw
					.getLocation().y);
				//fw.toFront();
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
		
	}
	
	private void closeActionBar() {
		frame.dispose();
		WindowManager.removeWindow(frame);
		WindowManager.setWindow(frontframe);
		shouldExit = true;
	}
	
	private void updateButtons() {
		Component[] a=frame.getContentPane().getComponents();
		for(int i=0; i<a.length; i++) {
			if (a[i] instanceof javax.swing.JToolBar) {
				Component[] b = ((JToolBar) a[i]).getComponents();
				for(int j=0; j<b.length; j++) {
					if (b[j] instanceof javax.swing.JButton) {
						// IJ.log(b[j].toString());
						String s=((JButton) b[j]).getActionCommand();
						String enabled = "";
						if (s.indexOf("<enabled>")>-1) enabled = s.substring(s.indexOf("<enabled>")+9);
						if (enabled!="") {
							//IJ.log(enabled);
							String ans = IJ.runMacro(enabled);
							if ((ans!=null)&&ans.startsWith("1")) ((JButton) b[j]).setEnabled(true);
							else ((JButton) b[j]).setEnabled(false);
						}
					}
				}
			}
		}
	}
	
	private void designPanel() {
		try {
// 			File file = new File(path);
// 			if (!file.exists())
// 				IJ.error("Config File not found");
// 			BufferedReader r = new BufferedReader(new FileReader(file));

                      Reader reader;
                      if (path.startsWith("jar:"))
                              reader = new InputStreamReader(new URL(path).openStream());
                       else {
                File file = new File(path);
                if (!file.exists()) {
                                  frame.dispose();
                                   IJ.error("Config File not found");
                            }
                             reader = new FileReader(file);
                     }
                      BufferedReader r = new BufferedReader(reader);


			while (true) {
				String s = r.readLine();
				if (s.equals(null)) {
					r.close();
					closeToolBar();
					break;
				} else if (s.startsWith("<icon>")) {
					String frameiconName = s.substring(6);
					setABIcon(frameiconName);
                } else if (s.startsWith("<main>")) {
					setABasMain();
					hideIJ();
                } else if (s.startsWith("<hideMenus>")) {
					captureMenus=false;
				} else if (s.startsWith("<popup>")) {
					isPopup = true;
				} else if (s.startsWith("<sticky>")) {
					isSticky = true;
				} else if (s.startsWith("<DnD>")) {
					setABDnD();
				} else if (s.startsWith("<beanshell>")) {
					setupBeanshell();
				} else if (s.startsWith("<onTop>")) {
					setABonTop();
				} else if (s.startsWith("<startupAction>")) {
					String code = "";
					while (true) {
						String sc = r.readLine();
						if (sc.equals(null)) {
							break;
						}
						if (!sc.startsWith("</startupAction>")) {
							code = code + "\n" + sc;
						} else {
							startupAction = code;
							break;
						}
					}
				} else if (s.startsWith("<DnDAction>")) {
					String code = "";
					while (true) {
						String sc = r.readLine();
						if (sc.equals(null)) {
							break;
						}
						if (!sc.startsWith("</DnDAction>")) {
							code = code + "\n" + sc;
						} else {
							DnDAction = code;
							break;
						}
					}
				} else if (s.startsWith("<codeLibrary>")) {
					String code = "";
					while (true) {
						String sc = r.readLine();
						if (sc.equals(null)) {
							break;
						}
						if (!sc.startsWith("</codeLibrary>")) {
							code = code + "\n" + sc;
						} else {
							codeLibrary = code;
							break;
						}
					}
				} else if (s.startsWith("<noGrid>") && tbOpenned == false) {
					grid = false;
				} else if (s.startsWith("<text>") && tbOpenned == false) {
					frame.getContentPane().add(new JLabel(s.substring(6)));
				} else if (s.startsWith("<line>") && tbOpenned == false) {
					toolBar = new JToolBar();
					nButtons = 0;
					tbOpenned = true;
				} else if (s.startsWith("</line>") && tbOpenned == true) {
					closeToolBar();
					tbOpenned = false;
				} else if (s.startsWith("<separator>") && tbOpenned == true) {
					toolBar.addSeparator();
				} else if (s.startsWith("<button>") && tbOpenned == true) {
					String enabled="";
					if (s.indexOf("<enabled>")>-1){
						enabled = s.substring(s.indexOf("<enabled>")+9);
					}
					//IJ.log(enabled);
					// read button attributes
					/*
					String label = r.readLine().substring(6);
					String icon = r.readLine().substring(5);
					String arg = r.readLine().substring(4);
					*/
					String label="",icon="noicon",arg="",bgcolor="";
					boolean argReached=false;
					do {
						String attribLine = r.readLine();
						if (attribLine==null) { 
						break;
						} else if (attribLine.trim().replace(" ","").startsWith("label=")){
						label = attribLine.substring(attribLine.indexOf("=")+1);
						} else if (attribLine.trim().replace(" ","").startsWith("icon=")){
						icon = attribLine.substring(attribLine.indexOf("=")+1);
						} else if (attribLine.trim().replace(" ","").startsWith("bgcolor=")){
						bgcolor = attribLine.substring(attribLine.indexOf("=")+1);
						} else if (attribLine.trim().replace(" ","").startsWith("arg=")){
						arg = attribLine.substring(attribLine.indexOf("=")+1);
						argReached=true;
						} 
					} while (!argReached);
					
					if (arg.startsWith("<bsh>")) {
						String code = "<bsh>";
						while (true) {
							String sc = r.readLine();
							if (sc.equals(null)) {
								break;
							}
							if (!sc.startsWith("</bsh>")) {
								code = code + "\n" + sc;
							} else {
								arg = code;
								break;
							}
						}
					} if (arg.startsWith("<macro>")) {
						String code = "";
						while (true) {
							String sc = r.readLine();
							if (sc.equals(null)) {
								break;
							}
							if (!sc.startsWith("</macro>")) {
								code = code + "\n" + sc;
							} else {
								arg = code;
								break;
							}
						}
					} else if (arg.startsWith("<tool>")) {
						String code = "<tool>\n";
						while (true) {
							String sc = r.readLine();
							if (sc.equals(null)) {
								break;
							}
							if (!sc.startsWith("</tool>")) {
								code = code + "\n" + sc;
							} else {
								arg = code;
								break;
							}
						}
					} else if (arg.startsWith("<hide>")) {
						arg = "<hide>";
					} else if (arg.startsWith("<close>")) {
						arg = "<close>";
					}
					if (enabled!="") arg=arg+"<enabled>"+enabled;
					
					button = makeNavigationButton(icon, arg, label, label, bgcolor);
					toolBar.add(button);
					nButtons++;
				}
			}
			r.close();
		} catch (Exception e) {
		}
	}
	
	private void setupBeanshell() {
		// TODO Auto-generated method stub
		Object BStest = IJ.runPlugIn("bsh", "return 1;");
		if ((BStest==null)&&!(IJ.getInstance().getTitle().indexOf("Fiji")>0)) {
			IJ.error("Is BeanShell installed?");
			somethingWentWrong=true;
		} 
		else bsh = new Interpreter();
	}

	private void closeToolBar() {
		toolBar.setFloatable(false);
		if (grid)
			toolBar.setLayout(new GridLayout(1, nButtons));
		frame.getContentPane().add(toolBar);
		tbOpenned = false;
	}
	
	protected JButton makeNavigationButton(String imageName,
		String actionCommand, String toolTipText, String altText, String color) {
	
	String imgLocation = imageName;
	if (!imgLocation.startsWith("icons/" )) imgLocation = "icons/"+imageName;
	URL imageURL = null;

	if (path.startsWith("jar:file:")) {
   	 String inputFile = path.substring(0, path.indexOf("!/")+2)+imgLocation;

   	 try {
   		imageURL = new URL(inputFile);
		} catch (MalformedURLException e) {
			IJ.error("Error getting icon file ressource from JAR file.");
			e.printStackTrace();
		}
	} else {
		try {
			File f = new File( IJ.getDirectory("plugins")+"ActionBar"+File.separator+imgLocation);
			imageURL = f.toURI().toURL();
		
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	JButton button = new JButton();
	button.setActionCommand(actionCommand);
	button.setMargin(new Insets(2, 2, 2, 2));
	// button.setBorderPainted(true);
	button.addActionListener(this);
	button.setFocusable(true);
	button.addKeyListener(IJ.getInstance());
	if ((imageURL != null)&&(!imageURL.toString().endsWith("noicon"))) {
		button.setIcon(new ImageIcon(imageURL, altText));
		button.setToolTipText(toolTipText);
	} else {
		button.setText(altText);
		button.setToolTipText(toolTipText);

	}
	if (color!="") {
		if (color.startsWith("#")) button.setBackground(Colors.decode(color,Color.gray));
		else button.setBackground(presetColors[Integer.parseInt(color)]);
	}
	
	return button;
		}
		
		public void actionPerformed(ActionEvent e) {
			
			String cmd = e.getActionCommand();
			// IJ.log(cmd);
			// remove the bit of macro that returns true is this button should be eenabled
			if (cmd.indexOf("<enabled>")>-1) cmd = cmd.substring(0,cmd.indexOf("<enabled>"));
			
			
			if (((e.getModifiers() & e.ALT_MASK) * ((e.getModifiers() & e.CTRL_MASK))) != 0) {
				if (!(path.startsWith("jar:"))) IJ.run("Edit...", "open=[" + path + "]");
				return;
			}
			if (((e.getModifiers() & e.ALT_MASK)) != 0) {
				closeActionBar();
				return;
			}
			if (cmd.startsWith("<hide>")) {
				toggleIJ();
			} else if (cmd.startsWith("<close>")) {
				closeActionBar();
				return;
			} else if (cmd.startsWith("<tool>")) {
				// install this tool and select it
				String tool = cmd.substring(6, cmd.length());
				String toolname = ((JButton) e.getSource()).getToolTipText();
				tool = "macro 'ActionBarTool Tool - C000'{\n" + tool + "\n}\n"
				+ codeLibrary;
				// Toolbar.removeMacroTools();
				new MacroInstaller().install(tool);
				Toolbar.getInstance().setTool("ActionBarTool");
				IJ.showStatus(toolname + " Tool installed");
				
			} else if (cmd.startsWith("<bsh>")) {
				// do something with the path
				bshPath = cmd.substring(5, cmd.length()).trim();
				evalBeanshell(bshPath);
				// IJ.showStatus("beanshell button:"+bshPath);
				
			} else {
				try {
					new MacroRunner(cmd + "\n" + codeLibrary);
				} catch (Exception fe) {
					IJ.error("Error in macro command");
				}
			}
			frame.repaint();
			if (isPopup) {
				frame.dispose();
				WindowManager.removeWindow(frame);
				WindowManager.setWindow(frontframe);
			}
			
		}
		
		private void evalBeanshell( final String bshScript) {
			// TODO Auto-generated method stub
			  imports =
					"import ij.*;"+
					"import ij.gui.*;"+
					"import ij.process.*;"+
					"import ij.measure.*;"+
					"import ij.util.*;"+
					"import ij.plugin.*;"+
					"import ij.io.*;"+
					"import ij.plugin.filter.*;"+
					"import ij.plugin.frame.*;"+
					"import java.lang.*;"+
					"import java.awt.*;"+
					"import java.awt.image.*;"+
					"import java.awt.geom.*;"+
					"import java.util.*;"+
					"import java.io.*;"+
					"print(arg) {IJ.log(\"\"+arg);}\n";
			try {
				if (bsh!=null) {
					Thread newThread = new Thread(new Runnable() { 
					    @Override 
					    public void run() { 
							try {
								bsh.eval(imports+bshScript);
							} catch (EvalError e) {
								e.printStackTrace();
							}
					    } 
					}); 
					newThread.start();
				}
				else {
					// general case, one interpreter per beanshell script
					IJ.runPlugIn("bsh", imports+bshPath);
				}
			} catch(Throwable e) {
				String msg = e.getMessage();
					IJ.log(msg);
				
			}
		}

		private void toggleIJ() {
			IJ.getInstance().setVisible(!IJ.getInstance().isVisible());
			visible = IJ.getInstance().isVisible();
		}
		
		private void hideIJ() {
			IJ.getInstance().setVisible(false);
			visible = false;
		}
		
		protected void rememberXYlocation() {
			Prefs.set("actionbar" + title + ".xloc", frame.getLocation().x);
			Prefs.set("actionbar" + title + ".yloc", frame.getLocation().y);
		}
		
		private void setABasMain() {
			frame.addWindowListener(new WindowAdapter() {
					public void windowClosing(WindowEvent e) {
						rememberXYlocation();
						e.getWindow().dispose();
						IJ.run("Quit");
					}
			});
		}
		
		private void setABDnD() {
			DropTarget dt = new DropTarget(frame, IJ.getInstance().getDropTarget());
		}
		
		private void setABIcon(String s) {
			try {
				
				String imgLocation = "icons/" + s;
				URL imageURL = getClass().getResource(imgLocation);
				Image img=Toolkit.getDefaultToolkit().getImage(imageURL);
				if (img!=null) frame.setIconImage(img);
			} catch (Exception fe) {
				IJ.error("Error creating the bar's icon");
			}
		}
		
		private void setABonTop() {
			frame.setAlwaysOnTop(true);
		}
		
       public static void callFunctionFinder() {
               new FunctionFinder();
       }

		         public static void pasteIntoEditor(String text) {
                 Component component = getTextArea();
                 if (component == null)
                         return;
                 if (component instanceof TextArea) {
                         TextArea textArea = (TextArea)component;
                         int begin = textArea.getSelectionStart();
                         int end = textArea.getSelectionEnd();
                         int pos;
                         if (begin < 0) {
                                 pos = textArea.getCaretPosition();
                                 textArea.insert(text, pos);
                                 pos += text.length();
                         }
                         else {
                                 try {
                                         textArea.replaceRange(text, begin, end);
                                         pos = begin + text.length();
                                 }
                                 catch (Exception e2) {
                                         return;
                                 }
                         }
                         textArea.setCaretPosition(pos);
                 }
                 else if (component instanceof JTextArea) {
                         JTextArea textArea = (JTextArea)component;
                         int begin = textArea.getSelectionStart();
                         int end = textArea.getSelectionEnd();
                         int pos;
                         if (begin < 0) {
                                 pos = textArea.getCaretPosition();
                                 textArea.insert(text, pos);
                                 pos += text.length();
                         }
                         else {
                                 try {
                                         textArea.replaceRange(text, begin, end);
                                         pos = begin + text.length();
                                 }
                                 catch (Exception e2) {
                                         return;
                                 }
                         }
                         textArea.setCaretPosition(pos);
                 }
         }
 
         protected static Component getTextArea() {
                 Frame front = WindowManager.getFrontWindow();
                 Component result = getTextArea(front);
                 if (result != null)
                         return result;
 
                 // look at the other frames
                 Frame[] frames = WindowManager.getNonImageWindows();
                 for (int i = frames.length - 1; i >= 0; i--) {
                         result = getTextArea(frames[i]);
                         if (result != null)
                                 return result;
                 }
                 return null;
         }
 
         protected static Component getTextArea(Container container) {
                 return getTextArea(container, 6);
         }
 
         protected static Component getTextArea(Container container, int maxDepth) {
                 if (container == null)
                         return null;
                 for (Component component : container.getComponents()) {
                         if ((component instanceof TextArea || component instanceof JTextArea) && component.isVisible())
                                 return component;
                         if (maxDepth > 0 && (component instanceof Container)) {
                                 Component result = getTextArea((Container)component);
                                 if (result != null)
                                         return result;
                         }
                 }
                 return null;
         }
		
		// Droptarget Listener methods
		public void drop(DropTargetDropEvent dtde)  {
			dtde.acceptDrop(DnDConstants.ACTION_COPY);
			DataFlavor[] flavors = null;
			try  {
				Transferable t = dtde.getTransferable();
				iterator = null;
				flavors = t.getTransferDataFlavors();
				if (IJ.debugMode) IJ.log("Droplet.drop: "+flavors.length+" flavors");
				for (int i=0; i<flavors.length; i++) {
					if (IJ.debugMode) IJ.log("  flavor["+i+"]: "+flavors[i].getMimeType());
					if (flavors[i].isFlavorJavaFileListType()) {
						Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
						iterator = ((java.util.List)data).iterator();
						break;
					} else if (flavors[i].isFlavorTextType()) {
						Object ob = t.getTransferData(flavors[i]);
						if (!(ob instanceof String)) continue;
						String s = ob.toString().trim();
						if (IJ.isLinux() && s.length()>1 && (int)s.charAt(1)==0)
							s = fixLinuxString(s);
						ArrayList list = new ArrayList();
						if (s.indexOf("href=\"")!=-1 || s.indexOf("src=\"")!=-1) {
							s = parseHTML(s);
							if (IJ.debugMode) IJ.log("  url: "+s);
							list.add(s);
							this.iterator = list.iterator();
							break;
						}
						BufferedReader br = new BufferedReader(new StringReader(s));
						String tmp;
						while (null != (tmp = br.readLine())) {
							tmp = java.net.URLDecoder.decode(tmp, "UTF-8");
							if (tmp.startsWith("file://")) tmp = tmp.substring(7);
							if (IJ.debugMode) IJ.log("  content: "+tmp);
							if (tmp.startsWith("http://"))
								list.add(s);
							else
								list.add(new File(tmp));
						}
						this.iterator = list.iterator();
						break;
					}
				}
				if (iterator!=null) {
					Thread thread = new Thread(this, "ActionBar_");
					thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
					thread.start();
				}
			}
			catch(Exception e)  {
				dtde.dropComplete(false);
				return;
			}
		}
		private String fixLinuxString(String s) {
			StringBuffer sb = new StringBuffer(200);
			for (int i=0; i<s.length(); i+=2)
				sb.append(s.charAt(i));
			return new String(sb);
		}
		
		private String parseHTML(String s) {
			if (IJ.debugMode) IJ.log("parseHTML:\n"+s);
			int index1 = s.indexOf("src=\"");
			if (index1>=0) {
				int index2 = s.indexOf("\"", index1+5);
				if (index2>0)
					return s.substring(index1+5, index2);
			}
			index1 = s.indexOf("href=\"");
			if (index1>=0) {
				int index2 = s.indexOf("\"", index1+6);
				if (index2>0)
					return s.substring(index1+6, index2);
			}
			return s;
		}
		
		public void dragEnter(DropTargetDragEvent e)  {
			IJ.showStatus("Drop here!");
			e.acceptDrag(DnDConstants.ACTION_COPY);
		}
		
		public void dragOver(DropTargetDragEvent e) {
			IJ.showStatus("Drop here!");
		}
		
		public void dragExit(DropTargetEvent e) {
			IJ.showStatus("Drag files to process");
		}
		
		public void dropActionChanged(DropTargetDragEvent e) {}
		
		public void run() {
			Iterator iterator = this.iterator;
			while(iterator.hasNext()) {
				Object obj = iterator.next();
				try {
					File f = (File)obj;
					String path = f.getCanonicalPath();
					IJ.runMacro(DnDAction,path);;
				} catch (Throwable e) {
					if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
						IJ.handleException(e);
				}
			}
			IJ.showStatus("Drag files to process");
		}
		
    void showAbout() {
        GenericDialog gd = new GenericDialog("About Action Bar...");
        gd.addMessage("Action Bar by Jerome Mutterer");
        gd.addMessage("For more info, press 'Help'");
        gd.addMessage("Version: " + VERSION);
        gd.addHelp("http://imagejdocu.tudor.lu/doku.php?id=plugin:utilities:action_bar:start");
        gd.hideCancelButton();
        gd.showDialog();
    }
}
		
