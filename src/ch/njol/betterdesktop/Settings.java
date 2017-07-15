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

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.eclipse.jdt.annotation.Nullable;

import ch.njol.betterdesktop.MonitoredValue.MonitoredBoolean;
import ch.njol.betterdesktop.MonitoredValue.MonitoredInteger;
import ch.njol.betterdesktop.MonitoredValue.MonitoredString;

// Not thread safe, but will only be used by the main thread in the beginning, the AWT even thread in the middle, and a shutdown hook thread in the end.
public class Settings extends PropertiesEx {
	
	public static final Settings INSTANCE = new Settings();
	
	// actual settings
	
	public MonitoredString directory = new MonitoredString(System.getProperty("user.home") + "/Documents/Motunautr/");
	
	public MonitoredBoolean blurBackground = new MonitoredBoolean(true);
	public MonitoredInteger mainWindowOpacity = new MonitoredInteger(127);
	
	public MonitoredBoolean showOnDesktop = new MonitoredBoolean(true);
	
	public MonitoredBoolean excludeFromPeek = new MonitoredBoolean(true);
	
	public MonitoredBoolean moveToFrontOnWindowsKey = new MonitoredBoolean(true);
	
	// settings window
	
	private static volatile @Nullable JFrame settingsWindow = null;
	
	public final static synchronized JFrame getSettingsWindow() {
		if (settingsWindow != null)
			return settingsWindow;
		final JFrame f = new JFrame(Main.NAME + ": Settings");
		f.setIconImage(Main.icon);
		f.setContentPane(new JPanel());
		((JPanel) f.getContentPane()).setBorder(new EmptyBorder(4, 8, 4, 8));
		f.setLayout(new BoxLayout(f.getContentPane(), BoxLayout.Y_AXIS));
		
		// folder
		JPanel o = new JPanel();
		o.setLayout(new BoxLayout(o, BoxLayout.X_AXIS));
		String folder;
		try {
			folder = new File(INSTANCE.directory.get()).getCanonicalPath();
		} catch (final IOException e) {
			folder = INSTANCE.directory.get();
		}
		final JTextField folderField = new JTextField(folder);
		folderField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(final DocumentEvent e) {
				update();
			}
			
			@Override
			public void insertUpdate(final DocumentEvent e) {
				update();
			}
			
			@Override
			public void changedUpdate(final DocumentEvent e) {
				update();
			}
			
			final char[] disallowedChars = "<>?|*\"".toCharArray();
			
			void update() {
				String f = folderField.getText();
				if (f == null)
					f = "";
				for (final char c : disallowedChars) {
					if (f.indexOf(c) >= 0)
						f = f.replace("" + c, "");
				}
				if (!f.equals(folderField.getText()))
					folderField.setText(f);
				if (new File(f).isDirectory()) {
					folderField.setForeground(new Color(0, 0, 0));
				} else {
					folderField.setForeground(new Color(127, 0, 0));
				}
			}
		});
		o.add(new JLabel("Main folder: "));
		o.add(folderField);
		o.add(new JButton(new AbstractAction("...") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final JFileChooser fc = new JFileChooser(INSTANCE.directory.get());
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (fc.showDialog(f, "Select Directory") == JFileChooser.APPROVE_OPTION) {
					try {
						folderField.setText(fc.getSelectedFile().getCanonicalPath());
					} catch (final IOException e1) {
						folderField.setText(fc.getSelectedFile().toString());
					}
				}
			}
		}));
		o.add(new JButton(new AbstractAction("Apply") {
			@Override
			public void actionPerformed(final ActionEvent e) {
				String filePath = folderField.getText();
				if (filePath == null)
					filePath = "";
				final File file = new File(filePath).getAbsoluteFile();
				try {
					Paths.get(file.toString());
				} catch (final InvalidPathException ex) {
					JOptionPane.showMessageDialog(f, "Invalid file path. Use the '...' button to manually select a path to get a valid path as starting point.", "Invalid path", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (!file.exists()) {
					final int result = JOptionPane.showConfirmDialog(f, "The folder '" + file + "' does not exist. Do you want to create it?", "Folder does not exist", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
					if (result != JOptionPane.OK_OPTION)
						return;
					if (!file.mkdirs()) {
						JOptionPane.showMessageDialog(f, "Could not create the directory. Make sure you have the rights to create it and any parent folders. Use a folder inside your Documents if possible.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				} else if (!file.isDirectory()) {
					JOptionPane.showMessageDialog(f, "Please select a folder, not a file.", "Error: file selected", JOptionPane.ERROR_MESSAGE);
					return;
				}
				folderField.setText(file.toString()); // update display (and possibly change to absolute path)
				INSTANCE.directory.set(file.toString());
			}
		}));
		f.add(o);
		
		// opacity
		o = new JPanel();
		o.setLayout(new BoxLayout(o, BoxLayout.X_AXIS));
		o.add(new JLabel("Background opacity: "));
		o.add(new JSlider(INSTANCE.mainWindowOpacity.createModel(1, 255)));
		f.add(o);
		
		// simple options
		f.add(new JCheckBox(INSTANCE.blurBackground.createAction("Blur background")));
		f.add(new JCheckBox(INSTANCE.moveToFrontOnWindowsKey.createAction("Show icons when the Windows key is pressed")));
		f.add(new JCheckBox(INSTANCE.showOnDesktop.createAction("Don't hide icons when showing desktop (requires restart)")));
		f.add(new JCheckBox(INSTANCE.excludeFromPeek.createAction("Keep icons visible while previewing the desktop")));
		
		f.pack();
		settingsWindow = f;
		return f;
	}
	
	// saving & loading
	
	private static @Nullable RandomAccessFile settingsFile;
	private static @Nullable FileChannel openChannel;
	private static @Nullable FileLock lock;
	
	public static void load() throws IOException {
		settingsFile = new RandomAccessFile("./settings.properties", "rw");
		openChannel = settingsFile.getChannel();
		lock = openChannel.tryLock();
		if (lock == null) {
			JOptionPane.showMessageDialog(null, "Already running!", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
		INSTANCE.load(Channels.newReader(openChannel, "utf-8"));
	}
	
	@SuppressWarnings("null")
	public static void save() throws IOException {
		openChannel.truncate(0);
		INSTANCE.save(Channels.newWriter(openChannel, "utf-8"));
	}
	
	@SuppressWarnings("null")
	public static void releaseLock() throws IOException {
		openChannel.close();
//		lock.release();
	}
	
}
