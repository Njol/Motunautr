/*
This file is part of Motunautr, an alternative to the default Windows desktop and start menu.
Copyright (C) 2017 Peter Güttinger

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package ch.njol.betterdesktop;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;

import ch.njol.betterdesktop.win32.Dwmapi;

public class Main {
	
	public final static String NAME = "Mǫtunautr";
	
	private final static List<BDWindow> windows = new ArrayList<>();
	
	@SuppressWarnings("null")
	public static Image icon;
	
	public static void main(final String[] args) throws IOException, AWTException {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		
		UIManager.put("ToolTipManager.enableToolTipMode", ""); // show tooltips even if this is not the active application
		ToolTipManager.sharedInstance().setInitialDelay(300);
		ToolTipManager.sharedInstance().setReshowDelay(0);
		
		PopupFactory.setSharedInstance(new PopupFactory() {
			@Override
			public @Nullable Popup getPopup(@Nullable final Component owner, final Component contents, final int x, final int y) throws IllegalArgumentException {
				final Popup p = super.getPopup(null, contents, x, y);
				Component c = contents;
				while (c != null && !(c instanceof Window)) {
					c.setBackground(new Color(255, 255, 255));
					c = c.getParent();
				}
				return p;
			}
		});
		
		Settings.load();
		
		icon = ImageIO.read(new File("./images/icon.png"));
		final JPopupMenu trayPopup = new JPopupMenu();
		trayPopup.add(new JMenuItem(new AbstractAction("settings...") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final JFrame f = Settings.getSettingsWindow();
				f.setVisible(true);
				f.setLocationRelativeTo(null);
			}
		}));
		trayPopup.add(new JMenuItem(new AbstractAction("help...") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final JFrame f = new JFrame(Main.NAME + ": Help");
				f.setIconImage(icon);
				final JTextPane text = new JTextPane();
				text.setText("Controls:\n"
						+ "Right click on item: expand (only if item is actually a folder)\n"
						+ "Double click on group title: open folder\n"
						+ "Middle click on item or group title: rename file or folder");
				text.setEditable(false);
				f.add(text);
				f.pack();
				f.setLocationRelativeTo(null);
				f.setVisible(true);
			}
		}));
		trayPopup.add(new JMenuItem(new AbstractAction("close") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				System.exit(0); // settings should be saved immediately at all times
			}
		}));
		final TrayIcon trayIcon = new TrayIcon(icon, NAME);
		trayIcon.setImageAutoSize(true);
		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				if (e.getButton() == 3) {
					final Point l = MouseInfo.getPointerInfo().getLocation();
					trayPopup.setLocation(l.x, l.y);
					trayPopup.setInvoker(trayPopup);
					trayPopup.setVisible(true);
				} else if (e.getButton() == 2) {
					System.exit(0);
				} else {
					for (final BDWindow w : windows) {
						w.toFront();
						w.setAlwaysOnTop(true); // this works, the above line does not...
						w.setAlwaysOnTop(false);
					}
				}
			}
		});
		SystemTray.getSystemTray().add(trayIcon);
		
		final File mainFolder = new File(Settings.directory);
		final @NonNull File[] contents = mainFolder.listFiles();
		assert contents != null;
		for (final File f : contents) {
			if (f.isHidden() || f.getName().startsWith(".") || !f.isDirectory())
				continue;
			final BDWindow w = new BDWindow(f);
			windows.add(w);
			w.pack();
			w.setVisible(true);
			
			ch.njol.betterdesktop.win32.User32.enableBlur(w);
			Dwmapi.setExcludedFromPeek(w, true);
		}
		
		// prevents "show desktop" button from hiding the windows
		final HWND progman = User32.INSTANCE.FindWindow("Progman", "Program Manager");
		if (progman != null) {
			for (final BDWindow w : windows) {
				User32.INSTANCE.SetWindowLongPtr(new HWND(Native.getComponentPointer(w)), WinUser.GWL_HWNDPARENT, progman.getPointer());
				w.setAlwaysOnTop(true); // not sure why this is required, maybe it just causes some update
				w.setAlwaysOnTop(false);
			}
		}
		
	}
	
}
