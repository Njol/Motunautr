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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import com.sun.jna.platform.win32.WinUser.MSG;

import ch.njol.betterdesktop.win32.Dwmapi;

public class Main {
	
	public final static String NAME = "Mǫtunautr";
	
	private final static List<BDWindow> windows = new ArrayList<>();
	
	public final static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(16, 16, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
	static {
		threadPool.allowCoreThreadTimeOut(true);
	}
	
	@SuppressWarnings("null")
	public static Image icon;
	
	public static void allToFront() {
		for (final BDWindow w : windows) {
			w.toFront();
			w.setAlwaysOnTop(true); // this works, the above line does not...
			w.setAlwaysOnTop(false);
		}
	}
	
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
					allToFront();
				}
			}
		});
		SystemTray.getSystemTray().add(trayIcon);
		
		final HWND progman = User32.INSTANCE.FindWindow("Progman", "Program Manager");
		
		final File mainFolder = new File(Settings.directory);
		final @NonNull File[] contents = mainFolder.listFiles();
		assert contents != null;
		for (final File f : contents) {
			if (f.isHidden() || f.getName().startsWith(".") || !f.isDirectory())
				continue;
			threadPool.execute(() -> {
				final BDWindow w = new BDWindow(f);
				windows.add(w);
				w.pack();
				w.setVisible(true);
				
				ch.njol.betterdesktop.win32.User32.enableBlur(w);
				Dwmapi.setExcludedFromPeek(w, true);
				
				// prevents "show desktop" button from hiding the windows
				if (progman != null) {
					User32.INSTANCE.SetWindowLongPtr(new HWND(Native.getComponentPointer(w)), WinUser.GWL_HWNDPARENT, progman.getPointer());
					w.setAlwaysOnTop(true); // not sure why this is required, maybe it just causes some update
					w.setAlwaysOnTop(false);
				}
				
			});
		}
		
		// make windows key move all BD windows to front
		final Thread t = new Thread(() -> {
			final int VK_LWIN = 0x5B, VK_RWIN = 0x5C;
			if (!User32.INSTANCE.RegisterHotKey(null, 1, WinUser.MOD_WIN, VK_LWIN)
					|| !User32.INSTANCE.RegisterHotKey(null, 2, WinUser.MOD_WIN, VK_RWIN))
				System.out.println("Failed to register windows key hooks");
			
			final MSG msg = new MSG();
			while (User32.INSTANCE.GetMessage(msg, null, WinUser.WM_HOTKEY, WinUser.WM_HOTKEY) > 0) {
				// wait for key release
				while (User32.INSTANCE.GetAsyncKeyState(VK_LWIN) < 0 || User32.INSTANCE.GetAsyncKeyState(VK_RWIN) < 0) {
					try {
						Thread.sleep(10);
					} catch (final InterruptedException e) {}
				}
				allToFront();
			}
		});
		t.setDaemon(true);
		t.start();
		
		try {
			Thread.sleep(10000);
		} catch (final InterruptedException e) {}
		
		// clean up cached images of files that don't exist any more
		for (final BDWindow w : windows) {
			Files.walk(w.metaFolder.toPath()).forEach(p -> {
				if (!p.getFileName().toString().endsWith(".png"))
					return;
				final Path file = FileIcon.fileFromIconCacheFile(w, p);
				if (file == null || !file.toFile().exists()) {
					System.out.println("Deleting cached icon for " + file);
					p.toFile().delete();
				}
			});
		}
		
	}
	
}
