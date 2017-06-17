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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.eclipse.jdt.annotation.Nullable;

public class Settings {
	
	// actual settings
	
	public static String directory = System.getProperty("user.home") + "/Documents/Startmenu/";
	
	public static float opacity = 0.7f; // TODO use
	
	// saving & loading
	
	private static @Nullable RandomAccessFile settingsFile;
	private static @Nullable FileChannel openChannel;
	private static @Nullable FileLock lock;
	
	@SuppressWarnings("null")
	public static void load() throws IOException {
		settingsFile = new RandomAccessFile("./settings.properties", "rw");
		openChannel = settingsFile.getChannel();
		lock = openChannel.tryLock();
		if (lock == null) {
			JOptionPane.showMessageDialog(null, "Already running!", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
	}
	
	@SuppressWarnings("null")
	public static void save() throws IOException {
		openChannel.close();
		lock.release();
	}
	
	// settings window
	
	private static volatile @Nullable JFrame settingsWindow = null;
	
	public final static synchronized JFrame getSettingsWindow() {
		if (settingsWindow != null)
			return settingsWindow;
		final JFrame f = new JFrame(Main.NAME + ": Settings");
		f.setIconImage(Main.icon);
		f.add(new JLabel("nothing to see here"));
		f.pack();
		settingsWindow = f;
		return f;
	}
	
}
