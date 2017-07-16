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
import java.awt.Desktop;
import java.awt.Dialog.ModalityType;
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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
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
	
	private final static ConcurrentLinkedDeque<BDWindow> windows = new ConcurrentLinkedDeque<>();
	
	private static volatile @Nullable FileWatcher fileWatcher = null;
	
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
	
	static boolean isFirstRun = false; // set by settings
	
	public static void main(final String[] args) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		
		UIManager.put("ToolTipManager.enableToolTipMode", ""); // show tooltips even if this is not the active application
		ToolTipManager.sharedInstance().setInitialDelay(300);
		ToolTipManager.sharedInstance().setReshowDelay(0);
		
		// change the ugly yellow tooltip background to white
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
		
		// settings
		try {
			Settings.load();
		} catch (final IOException e2) {
			JOptionPane.showMessageDialog(null, "Could not load settings: " + e2.getMessage() + ".\nThe default values will be used, including the default directory 'Desktop' in your documents folder.", NAME + ": Error", JOptionPane.ERROR_MESSAGE);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				Settings.save();
				Settings.releaseLock();
			} catch (final IOException e1) {
				e1.printStackTrace();
			}
		}));
		
		// tray icon
		try {
			icon = ImageIO.read(new File("./images/icon.png"));
		} catch (final IOException e2) {
			System.exit(1);
		}
		final JPopupMenu trayPopup = new JPopupMenu();
		trayPopup.add(new JMenuItem(new AbstractAction("settings...") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Settings.showSettingsWindow();
			}
		}));
		trayPopup.add(new JMenuItem(new AbstractAction("help...") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				HelpWindow.doShow();
			}
		}));
		trayPopup.add(new JMenuItem(new AbstractAction("close") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				System.exit(0); // settings should be saved immediately at all times or in shutdown hooks
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
		try {
			SystemTray.getSystemTray().add(trayIcon);
		} catch (final AWTException e2) {
			System.err.println("Could not add tray icon: " + e2.getMessage());
		}
		
		boolean mainFolderExisted = false;
		if (isFirstRun) {
			final File mainFolder = new File(Settings.INSTANCE.directory.get());
			if (mainFolder.isDirectory())
				mainFolderExisted = true;
			mainFolder.mkdirs();
		}
		Settings.INSTANCE.directory.addListener(s -> {
			final Path newFolder = Paths.get(s);
			final FileWatcher oldWatcher = fileWatcher;
			if ((oldWatcher == null || !newFolder.equals(oldWatcher.watchedDirectory)) && newFolder.toFile().isDirectory()) {
				if (oldWatcher != null)
					oldWatcher.close();
				try {
					final Path directory = Paths.get(s);
					final FileWatcher newFileWatcher = new FileWatcher(directory);
					fileWatcher = newFileWatcher;
					newFileWatcher.addListener(directory, new FileWatcher.DirectoryListener(500) {
						@Override
						public boolean ignoreChange(final Path path) {
							return !path.getParent().equals(directory) || path.toFile().exists() && !path.toFile().isDirectory();
						}
						
						@Override
						public void directoryChanged() {
							SwingUtilities.invokeLater(() -> {
								createOrUpdateWindows();
							});
						}
					});
				} catch (final IOException e1) {
					e1.printStackTrace();
				}
				for (final BDWindow w : windows) {
					w.dispose();
				}
				windows.clear();
				createOrUpdateWindows();
			}
		});
		
		windowsKeyHandler.start();
		
		if (isFirstRun) {
			final boolean mainFolderExisted2 = mainFolderExisted;
			SwingUtilities.invokeLater(() -> {
				final File mainFolder = new File(Settings.INSTANCE.directory.get());
				if (mainFolderExisted2) { // maybe the settings were lost or reset?
					JOptionPane.showMessageDialog(null, "Looks like Motunautr's settings were deleted or moved (or maybe you moved to a new computer?).\n"
							+ "The settings window will now open, allowing you to set the options anew.", NAME + ": First Run", JOptionPane.INFORMATION_MESSAGE);
					Settings.showSettingsWindow();
				} else {
					final File windowsDesktopFolder = new File(System.getProperty("user.home") + "/Desktop/");
					new File(mainFolder, "Example Group").mkdir();
					try {
						Desktop.getDesktop().open(windowsDesktopFolder);
						Desktop.getDesktop().open(mainFolder);
					} catch (final IOException e1) {
						e1.printStackTrace();
					}
					final JOptionPane p = new JOptionPane("Looks like this is your first time running Motunautr.\n"
							+ "Motunautr opened your Windows Desktop folder as well as Motunautr's folder so that you can move your links into Motunautr groups.\n"
							+ "There is an empty example group already created for you - it should be visible at the top left of your screen.\n"
							+ "You can fill it with icons by moving links from the Desktop folder into the 'Example Group' folder.\n"
							+ "You can rename, delete, or create groups by renaming, deleting, or creating folders in the 'Motunautr' folder.\n"
							+ "For more information, right click the Motunautr icon in your task bar and click on 'Help' in the shown menu.",
							JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION);
					final JDialog d = p.createDialog(NAME + ": First Run");
					d.pack();
					d.setModalityType(ModalityType.MODELESS); // don't block other windows
					d.setVisible(true);
					d.setAlwaysOnTop(true); // the opened explorer windows are a bit delayed
					d.requestFocus();
					d.toFront();
				}
			});
		}
		
		// clean up cached images of files that don't exist any more after a while
		try {
			Thread.sleep(10000);
		} catch (final InterruptedException e) {}
		for (final BDWindow w : windows) {
			try {
				Files.walkFileTree(w.metaFolder.toPath(), new FileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
						if (file.getFileName().toString().endsWith(".png")) {
							final Path actualFile = FileIcon.fileFromIconCacheFile(w, file);
							if (actualFile == null || !actualFile.toFile().exists()) {
								System.out.println("Deleting cached icon for " + actualFile);
								file.toFile().delete();
							}
						}
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
						return FileVisitResult.CONTINUE;
					}
					
					@Override
					public FileVisitResult postVisitDirectory(final Path dir, final @Nullable IOException exc) throws IOException {
						final File f = dir.toFile();
						if (f.list().length == 0) {
							System.out.println("Deleting empty directory " + f);
							f.delete();
						}
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (final IOException e1) {
				// ignore
			}
		}
		
	}
	
	public static void watchDirectory(final Path relativePath, final FileWatcher.FileListener listener) {
		final FileWatcher fileWatcher = Main.fileWatcher;
		if (fileWatcher != null)
			fileWatcher.addListener(relativePath, listener);
	}
	
	private static void createOrUpdateWindows() {
		final File mainFolder = new File(Settings.INSTANCE.directory.get());
		final @NonNull File[] contents = mainFolder.listFiles();
		if (contents != null) {
			// remove old windows
			outer: for (final BDWindow w : windows) {
				for (final File f : contents) {
					if (w.folder.equals(f))
						continue outer;
				}
				System.out.println("Removing old window for " + w.folder);
				w.dispose();
				windows.remove(w);
			}
			
			// add new windows
			outer: for (final File f : contents) {
				if (f.isHidden() || f.getName().startsWith(".") || !f.isDirectory())
					continue;
				for (final BDWindow w : windows) {
					if (w.folder.equals(f))
						continue outer;
				}
				System.out.println("Adding new window for " + f);
				createWindow(f);
			}
		} else {
			for (final BDWindow w : windows) {
				w.dispose();
			}
			windows.clear();
		}
	}
	
	private static void createWindow(final File f) {
		SwingUtilities.invokeLater(() -> {
			if (!f.isDirectory()) // if the file was changed or deleted before this could execute, exit
				return;
			final BDWindow w = new BDWindow(f);
			windows.add(w);
			w.pack();
			w.setVisible(true);
			
			threadPool.execute(() -> {
				// sets whether the windows blur the background behind them
				Settings.INSTANCE.blurBackground.addListener(enabled -> {
					ch.njol.betterdesktop.win32.User32.enableBlur(w, enabled);
				});
				
				// disables the windows from being hidden when "peeking" the desktop
				Settings.INSTANCE.excludeFromPeek.addListener(enabled -> {
					Dwmapi.setExcludedFromPeek(w, enabled);
				});
				
				// prevents "show desktop" button from hiding the windows
				if (Settings.INSTANCE.showOnDesktop.get()) {
					final HWND progman = User32.INSTANCE.FindWindow("Progman", "Program Manager");
					User32.INSTANCE.SetWindowLongPtr(new HWND(Native.getComponentPointer(w)), WinUser.GWL_HWNDPARENT, progman.getPointer());
					// the following is required so that this setting actually does something - no idea why though
					try {
						Thread.sleep(100);
					} catch (final InterruptedException e1) {}
					w.setAlwaysOnTop(true);
					w.setAlwaysOnTop(false);
				}
			});
		});
	}
	
	private static final Thread windowsKeyHandler = new Thread(() -> {
		// if disabled in the beginning, don't even register
		while (!Settings.INSTANCE.moveToFrontOnWindowsKey.get()) {
			try {
				synchronized (Settings.INSTANCE.moveToFrontOnWindowsKey) {
					Settings.INSTANCE.moveToFrontOnWindowsKey.wait();
				}
			} catch (final InterruptedException e) {}
		}
		
		final int VK_LWIN = 0x5B, VK_RWIN = 0x5C;
		if (!User32.INSTANCE.RegisterHotKey(null, 1, WinUser.MOD_WIN, VK_LWIN)
				|| !User32.INSTANCE.RegisterHotKey(null, 2, WinUser.MOD_WIN, VK_RWIN))
			System.out.println("Failed to register windows key hooks");
		
		final MSG msg = new MSG();
		while (User32.INSTANCE.GetMessage(msg, null, WinUser.WM_HOTKEY, WinUser.WM_HOTKEY) > 0) {
			if (!Settings.INSTANCE.moveToFrontOnWindowsKey.get())
				continue; // don't bother unregistering if the setting changed while active
				
			// wait for key release
			while (User32.INSTANCE.GetAsyncKeyState(VK_LWIN) < 0 || User32.INSTANCE.GetAsyncKeyState(VK_RWIN) < 0) {
				try {
					Thread.sleep(10);
				} catch (final InterruptedException e) {}
			}
			allToFront();
		}
	}, "Windows-key handler");
	static {
		windowsKeyHandler.setDaemon(true);
	}
	
}
